package com.example.myapplication.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A video or photo used by a footage layer. Frames are mirrored into a downscaled JPEG proxy
 * (like Alight Motion / Premiere proxies) so preview, scrubbing and tracking stay fast.
 */
data class Footage(
    val id: String,
    val uri: String,
    val isImage: Boolean,
    val width: Int,
    val height: Int,
    val duration: Float,
    val fps: Float,
    val frameCount: Int,
    val proxyDir: String,
) {
    val aspect: Float get() = width.toFloat() / height

    fun frameIndex(sourceTime: Float) = if (isImage) 0 else (sourceTime * fps).toInt().coerceIn(0, frameCount - 1)

    fun proxyFile(i: Int) = File(proxyDir, "f%05d.jpg".format(i))
}

object FootageImporter {
    private const val PROXY_LONG = 640
    private const val PHOTO_LONG = 2048

    /** Reads a video or photo and builds its proxy; [maxSeconds] caps how much of a long video is used. */
    suspend fun import(context: Context, uri: Uri, maxSeconds: Float, onProgress: (Float) -> Unit): Footage = withContext(Dispatchers.IO) {
        val id = "ft-" + java.util.UUID.randomUUID().toString()
        val dir = File(context.filesDir, "media/$id")
        check(dir.mkdirs()) { "Penyimpanan media tidak tersedia." }
        try {
        val type = context.contentResolver.getType(uri)
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()).orEmpty()
        // Keep the source too: gallery deletion or an expired URI grant must not break export.
        val source = File(dir, "source")
        context.contentResolver.openInputStream(uri)?.use { input ->
            source.outputStream().use { output ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(bytes)
                    if (n < 0) break
                    output.write(bytes, 0, n)
                }
            }
        } ?: throw ImportException("Media tidak bisa dibaca")
        val durableUri = Uri.fromFile(source)
        if (type.startsWith("image/")) importPhoto(context, durableUri, id, dir) else importVideo(context, durableUri, id, dir, maxSeconds, onProgress)
        } catch(e:Throwable) {
            // This freshly allocated UUID directory belongs only to this incomplete import.
            dir.deleteRecursively()
            throw e
        }
    }

    private fun importPhoto(context: Context, uri: Uri, id: String, dir: File): Footage {
        // ImageDecoder applies EXIF rotation, so portrait photos come out upright.
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val long = max(info.size.width, info.size.height)
            if (long > PHOTO_LONG) decoder.setTargetSampleSize(Integer.highestOneBit(long / PHOTO_LONG).coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            // PNG preserves transparency in imported logos and cutouts.
            FileOutputStream(File(dir, "f00000.jpg")).use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return Footage(id, uri.toString(), true, bmp.width, bmp.height, 0f, 1f, 1, dir.path)
        } finally { bmp.recycle() }
    }

    private suspend fun importVideo(context: Context, uri: Uri, id: String, dir: File, maxSeconds: Float, onProgress: (Float) -> Unit): Footage {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            fun meta(key: Int) = mmr.extractMetadata(key)
            val rawW = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: throw ImportException("Video tidak bisa dibaca.")
            val rawH = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: throw ImportException("Video tidak bisa dibaca.")
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val totalFrames = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            if (durationMs <= 0 || totalFrames <= 0) throw ImportException("Video ini tidak punya data frame yang bisa dibaca.")
            val (w, h) = if (rotation % 180 != 0) rawH to rawW else rawW to rawH
            val fps = (totalFrames / (durationMs / 1000f)).coerceIn(1f, 240f)
            val count = minOf(totalFrames, (maxSeconds * fps).roundToInt().coerceAtLeast(1))
            val scale = PROXY_LONG.toFloat() / max(w, h)
            val pw = ((w * minOf(1f, scale)).roundToInt() / 2) * 2
            val ph = ((h * minOf(1f, scale)).roundToInt() / 2) * 2
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            // Frames decode at full size: above 1080p one at a time keeps a 4K import within a small phone's heap.
            val perBatch = if (rawW.toLong() * rawH > 1920L * 1088) 1 else 4
            var i = 0
            while (i < count) {
                currentCoroutineContext().ensureActive()
                val batch = mmr.getFramesAtIndex(i, minOf(perBatch, count - i), params)
                if (batch.isEmpty()) break
                for (frame in batch) {
                    val upright = upright(frame, w, h, rotation)
                    val small = Bitmap.createScaledBitmap(upright, pw, ph, true)
                    FileOutputStream(File(dir, "f%05d.jpg".format(i))).use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    if (small !== upright) small.recycle()
                    if (upright !== frame) upright.recycle()
                    frame.recycle()
                    i++
                }
                onProgress(i.toFloat() / count)
            }
            if (i == 0) throw ImportException("Frame video tidak bisa dibaca.")
            return Footage(id, uri.toString(), false, w, h, i / fps, fps, i, dir.path)
        } finally {
            mmr.release()
        }
    }
}

/** Some decoders return frames as stored; turn them to match the display orientation. */
internal fun upright(frame: Bitmap, w: Int, h: Int, rotation: Int): Bitmap {
    val landscapeWanted = w >= h
    val landscapeGot = frame.width >= frame.height
    if (rotation % 180 == 0 || landscapeWanted == landscapeGot) return frame
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}

/** Where the renderer gets footage pixels from: the proxy cache while editing, the source while exporting. */
fun interface FrameSource {
    fun frame(footage: Footage, sourceTime: Float): ImageBitmap?
}

/**
 * Preview frames from the proxy, decoded on background threads into a byte-bounded cache.
 * A missing frame returns its nearest cached neighbour and is loaded for the next draw.
 */
object FootageFrames : FrameSource {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** Bumped whenever a frame arrives; draw code reads it so the preview redraws. */
    var version by mutableIntStateOf(0)
        private set

    override fun frame(footage: Footage, sourceTime: Float): ImageBitmap? {
        val index = footage.frameIndex(sourceTime)
        val hit = cache.get(key(footage, index))
        for (k in 1..3) request(footage, index + k)
        if (hit != null) return hit
        request(footage, index)
        for (d in 1..15) {
            cache.get(key(footage, index - d))?.let { return it }
            cache.get(key(footage, index + d))?.let { return it }
        }
        return null
    }

    private fun key(f: Footage, i: Int) = f.id + ":" + i

    private fun request(f: Footage, i: Int) {
        if (i !in 0 until f.frameCount) return
        val key = key(f, i)
        if (cache.get(key) != null || pending.size > 10 || !pending.add(key)) return
        pool.execute {
            try {
                BitmapFactory.decodeFile(f.proxyFile(i).path)?.let { cache.put(key, it.asImageBitmap()) }
            } finally {
                pending.remove(key)
                main.post { version++ }
            }
        }
    }
}

/**
 * Export frames straight from the source video at full quality. Frames are requested in time
 * order, so they are decoded sequentially in small batches.
 */
class ExportFrames(private val context: Context, private val maxLong: Int) : FrameSource, AutoCloseable {
    private val retrievers = HashMap<String, MediaMetadataRetriever>()
    private val batches = HashMap<String, Pair<Int, List<ImageBitmap>>>()
    private val photos = HashMap<String, ImageBitmap>()

    override fun frame(footage: Footage, sourceTime: Float): ImageBitmap? {
        if (footage.isImage) return photos.getOrPut(footage.id) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,Uri.parse(footage.uri))) { decoder,info,_ ->
                val scale=minOf(1f,maxLong.toFloat()/max(info.size.width,info.size.height))
                decoder.setTargetSize((info.size.width*scale).roundToInt().coerceAtLeast(1),(info.size.height*scale).roundToInt().coerceAtLeast(1))
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }
        val index = footage.frameIndex(sourceTime)
        batches[footage.id]?.let { (first, frames) -> frames.getOrNull(index - first)?.let { return it } }
        val mmr = retrievers.getOrPut(footage.id) {
            val reader=MediaMetadataRetriever()
            try { reader.setDataSource(context,Uri.parse(footage.uri)); reader }
            catch(e:Throwable) { reader.release(); throw e }
        }
        val batchSize=if(footage.width.toLong()*footage.height>1920L*1088) 1 else 3
        val n = minOf(batchSize, footage.frameCount - index)
        val raw = runCatching {
            mmr.getFramesAtIndex(index, n, MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 })
        }.getOrNull()
        // Export must wait for real pixels; an asynchronous preview-cache fallback can emit blanks.
        val decoded=raw?.takeIf { it.isNotEmpty() } ?: listOf(
            mmr.getFrameAtTime((index/footage.fps*1_000_000).toLong(),MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw ImportException("Frame sumber $index tidak dapat dibaca saat ekspor.")
        )
        val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val frames = decoded.map { b ->
            val up = upright(b, footage.width, footage.height, rotation)
            val scale = minOf(1f, maxLong.toFloat() / max(up.width, up.height))
            val out = if (scale < 1f) Bitmap.createScaledBitmap(up, (up.width * scale).roundToInt(), (up.height * scale).roundToInt(), true) else up
            if (out !== up) up.recycle()
            if (up !== b) b.recycle()
            out.asImageBitmap()
        }
        batches[footage.id] = index to frames
        return frames.first()
    }

    override fun close() {
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear()
        batches.clear()
        photos.clear()
    }
}
