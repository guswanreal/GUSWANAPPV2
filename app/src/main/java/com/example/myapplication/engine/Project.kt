package com.example.myapplication.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

// Composition settings chosen when a project is created, Alight Motion / After Effects style.

enum class AspectRatio(val label: String, val w: Int, val h: Int, val hint: String) {
    Landscape("16:9", 16, 9, "YouTube"),
    Portrait("9:16", 9, 16, "TikTok · Reels"),
    Square("1:1", 1, 1, "Feed"),
    Feed("4:5", 4, 5, "Instagram"),
    Classic("4:3", 4, 3, "Klasik"),
    Cinema("21:9", 21, 9, "Sinema"),
}

/** Resolutions are named by their short side, the way phones and editors label them. */
enum class Resolution(val label: String, val short: Int) {
    P360("360p", 360), P480("480p", 480), P540("540p", 540), P720("720p", 720),
    P1080("1080p", 1080), P1440("2K", 1440), P2160("4K", 2160),
}

val FrameRates = listOf(24, 25, 30, 50, 60, 90, 120)

val DefaultBackground = Color(0xFF05070F)

data class ProjectSettings(
    val name: String = "Proyek baru",
    val aspect: AspectRatio = AspectRatio.Landscape,
    val resolution: Resolution = Resolution.P1080,
    val fps: Int = 30,
    val duration: Float = DemoProjects.DURATION,
    val background: Color = DefaultBackground,
) {
    val width: Int get() = sizeFor(resolution).first
    val height: Int get() = sizeFor(resolution).second
    val aspectValue: Float get() = aspect.w.toFloat() / aspect.h

    /** Pixel size at [r], rounded to even numbers as H.264 requires. */
    fun sizeFor(r: Resolution): Pair<Int, Int> {
        val long = (r.short * maxOf(aspect.w, aspect.h).toFloat() / minOf(aspect.w, aspect.h)).roundToInt()
        val (w, h) = if (aspect.w >= aspect.h) long to r.short else r.short to long
        return (w - w % 2) to (h - h % 2)
    }
}

/**
 * What this phone's H.264 encoders can produce. A size the encoder only accepts sideways is
 * rendered rotated and tagged with orientation metadata, so 9:16 still exports on phones whose
 * encoder tops out at landscape sizes.
 */
class EncoderCaps private constructor(private val encoders: List<Pair<String, MediaCodecInfo.VideoCapabilities>>) {
    class Choice(val codecName: String, val rotated: Boolean, val bitrate: Int)

    fun choose(width: Int, height: Int, fps: Int): Choice? {
        for (rotated in listOf(false, true)) {
            val (w, h) = if (rotated) height to width else width to height
            val hit = encoders.firstOrNull { (_, c) -> runCatching { c.areSizeAndRateSupported(w, h, fps.toDouble()) }.getOrDefault(false) }
            if (hit != null) {
                // About 0.2 bits per pixel per frame, clamped to what the encoder accepts.
                val wanted = (width.toLong() * height * fps / 5).coerceIn(1_000_000L, 120_000_000L).toInt()
                return Choice(hit.first, rotated, hit.second.bitrateRange.clamp(wanted))
            }
        }
        return null
    }

    fun supports(width: Int, height: Int, fps: Int) = choose(width, height, fps) != null

    companion object {
        @Volatile private var cached: EncoderCaps? = null

        /** Hardware encoders first; the software encoder is the fallback. Call off the main thread. */
        fun get(): EncoderCaps = cached ?: EncoderCaps(
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } }
                .sortedBy { if (it.isHardwareAccelerated) 0 else 1 }
                .mapNotNull { info ->
                    val caps = runCatching { info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities }.getOrNull()
                    caps?.let { info.name to it }
                },
        ).also { cached = it }
    }
}
