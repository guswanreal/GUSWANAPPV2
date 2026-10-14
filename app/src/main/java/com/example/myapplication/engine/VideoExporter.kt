package com.example.myapplication.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Renders a composition frame by frame and encodes it to H.264 MP4 with MediaCodec.
 * Frames are drawn with the same [drawComposition] used by the live preview.
 */
object VideoExporter {
    suspend fun export(
        context: Context,
        uri: Uri,
        layers: List<Layer>,
        duration: Float,
        width: Int,
        height: Int,
        fps: Int,
        background: Color,
        fontResolver: FontFamily.Resolver,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        require(duration.isFinite() && duration > 0f && fps in 1..240)
        val jobContext = currentCoroutineContext()
        val choice = EncoderCaps.get().choose(width, height, fps)
            ?: error("Encoder HP ini tidak sanggup ${width}×${height} @ $fps fps. Pilih resolusi atau frame rate lebih rendah.")
        // A sideways-only encoder gets landscape frames plus a 90° orientation tag for players.
        val (encW, encH) = if (choice.rotated) height to width else width to height
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: error("Tidak bisa membuka file tujuan")
        pfd.use {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, choice.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val codec = MediaCodec.createByCodecName(choice.codecName)
            var ownedSurface: android.view.Surface? = null
            var ownedRenderer: android.graphics.HardwareRenderer? = null
            var ownedMuxer: MediaMuxer? = null
            var muxing = false
            val sourceFrames = ExportFrames(context, maxOf(width,height))
            try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface().also { ownedSurface = it }
            codec.start()
            val root = android.graphics.RenderNode("Video export").apply { setPosition(0,0,encW,encH) }
            val renderer = android.graphics.HardwareRenderer().also { ownedRenderer = it }
            renderer.setSurface(surface)
            renderer.setContentRoot(root)
            renderer.isOpaque = true
            renderer.start()
            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { ownedMuxer = it }
            if (choice.rotated) muxer.setOrientationHint(90)
            var track = -1
            var written = 0L
            val info = MediaCodec.BufferInfo()

            // Canvas-locked surfaces can't carry timestamps, so pts are assigned from the frame count.
            fun drain(endOfStream: Boolean) {
                if (endOfStream) codec.signalEndOfInputStream()
                var lastOutput = android.os.SystemClock.elapsedRealtime()
                while (true) {
                    jobContext.ensureActive()
                    check(!endOfStream || android.os.SystemClock.elapsedRealtime() - lastOutput < 30_000L) {
                        "Encoder tidak menyelesaikan video. Coba resolusi lebih rendah."
                    }
                    val idx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxing = true
                        }
                        idx >= 0 -> {
                            lastOutput = android.os.SystemClock.elapsedRealtime()
                            val buf = codec.getOutputBuffer(idx)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (buf != null && info.size > 0 && muxing) {
                                info.presentationTimeUs = written * 1_000_000L / fps
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                muxer.writeSampleData(track, buf, info)
                                written++
                            }
                            codec.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

                val scope = CanvasDrawScope()
                val density = Density(1f)
                val measurer = TextMeasurer(fontResolver, density, LayoutDirection.Ltr, COMPOSITION_TEXT_CACHE)
                val frames = (duration * fps).toInt().coerceAtLeast(1)
                for (i in 0 until frames) {
                    ensureActive()
                    val t = i.toFloat() / fps
                    // Own the renderer explicitly: its EGL producer must die BEFORE the codec consumer.
                    val hw = root.beginRecording(encW,encH)
                    try {
                        if (choice.rotated) {
                            // Store the upright frame turned 90° counter-clockwise; the tag turns it back on playback.
                            hw.translate(0f, encH.toFloat())
                            hw.rotate(-90f)
                        }
                        scope.draw(density, LayoutDirection.Ltr, Canvas(hw), Size(width.toFloat(), height.toFloat())) {
                            drawComposition(layers, t, measurer, background = background, frames = sourceFrames, fps = fps)
                        }
                    } finally {
                        root.endRecording()
                    }
                    val committed=java.util.concurrent.CountDownLatch(1)
                    val result=renderer.createRenderRequest().setVsyncTime(System.nanoTime())
                        .setFrameCommitCallback({ task -> task.run() }, { committed.countDown() })
                        .setWaitForPresent(true).syncAndDraw()
                    check(result and (android.graphics.HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or
                        android.graphics.HardwareRenderer.SYNC_CONTEXT_IS_STOPPED)==0) { "GPU gagal menyelesaikan frame ekspor ($result)." }
                    // A dropped vsync is rescheduled by HWUI. Keep this exact frame until it commits.
                    val deadline=android.os.SystemClock.elapsedRealtime()+30_000L
                    while(!committed.await(100,java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        ensureActive()
                        check(android.os.SystemClock.elapsedRealtime()<deadline) { "GPU tidak merespons saat ekspor." }
                    }
                    drain(false)
                    onProgress((i + 1f) / frames)
                }
                renderer.stop()
                renderer.destroy()
                ownedRenderer = null
                root.discardDisplayList()
                surface.release()
                ownedSurface = null
                drain(true)
            } finally {
                runCatching { sourceFrames.close() }
                runCatching { ownedRenderer?.stop() }
                runCatching { ownedRenderer?.destroy() }
                runCatching { ownedSurface?.release() }
                runCatching { codec.stop() }
                runCatching { codec.release() }
                if (muxing) runCatching { ownedMuxer?.stop() }
                runCatching { ownedMuxer?.release() }
            }
        }
    }
}
