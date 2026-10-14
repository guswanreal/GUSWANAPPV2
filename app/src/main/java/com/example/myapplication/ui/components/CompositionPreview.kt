package com.example.myapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.myapplication.engine.DemoProjects
import com.example.myapplication.engine.COMPOSITION_TEXT_CACHE
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.drawComposition
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.MonoLabel
import kotlin.math.floor

/**
 * Thumbnails animate at most this often: smooth for a preview, and half the rendering of a 60 Hz
 * screen (a quarter at 120 Hz), which keeps lists fluid on low-end phones.
 */
const val PREVIEW_FPS = 30

/** Screens provide true while their list scrolls, so thumbnails hold still and scrolling gets the whole frame. */
val LocalPreviewsPaused = compositionLocalOf { false }

/** Loops a composition on its own clock; used for template and project thumbnails. */
@Composable
fun CompositionPreview(
    layers: List<Layer>,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    stillTime: Float = 4f,
    duration: Float = DemoProjects.DURATION,
    scrubber: Boolean = false,
) {
    val measurer = rememberTextMeasurer(COMPOSITION_TEXT_CACHE)
    var time by remember { mutableFloatStateOf(stillTime) }
    val paused = LocalPreviewsPaused.current
    LaunchedEffect(playing, paused) {
        if (!playing) { time = stillTime; return@LaunchedEffect }
        // Paused keeps the current frame, so scrolling never makes a preview jump.
        if (paused) return@LaunchedEffect
        var clock = time
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                clock = (clock + (now - last) / 1e9f) % duration
                last = now
                // Only whole preview frames are drawn; writing the same value again doesn't redraw.
                time = floor(clock * PREVIEW_FPS) / PREVIEW_FPS
            }
        }
    }
    Canvas(modifier.clipToBounds()) {
        drawComposition(layers, time, measurer)
        if (scrubber) drawScrubber(time, duration, measurer)
    }
}

/** NLE-style transport strip: SMPTE-like timecode and a progress rail with the playhead. */
private fun DrawScope.drawScrubber(time: Float, duration: Float, measurer: TextMeasurer) {
    val rail = 3.dp.toPx()
    val y = size.height - rail
    drawRect(Color.White.copy(alpha = 0.14f), Offset(0f, y), Size(size.width, rail))
    drawRect(Accent, Offset(0f, y), Size(size.width * (time / duration), rail))
    val secs = time.toInt()
    val frames = ((time - secs) * 30).toInt()
    val total = duration.toInt()
    val label = "%02d:%02d:%02d / %02d:%02d:00".format(secs / 60, secs % 60, frames, total / 60, total % 60)
    val text = measurer.measure(label, MonoLabel.copy(color = Color.White))
    val pad = 8.dp.toPx()
    val origin = Offset(pad, y - pad - text.size.height)
    drawRect(Color.Black.copy(alpha = 0.55f), origin - Offset(5.dp.toPx(), 3.dp.toPx()), Size(text.size.width + 10.dp.toPx(), text.size.height + 6.dp.toPx()))
    drawText(text, topLeft = origin)
}
