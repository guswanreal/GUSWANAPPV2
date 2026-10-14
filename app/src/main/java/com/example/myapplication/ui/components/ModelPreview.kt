package com.example.myapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.myapplication.engine.Camera
import com.example.myapplication.engine.LibraryModel
import com.example.myapplication.engine.RenderItem
import com.example.myapplication.engine.Transform
import com.example.myapplication.engine.Vec3
import com.example.myapplication.engine.drawScene
import kotlin.math.floor

private const val TURNTABLE_DEG_PER_SEC = 40f

/** A slowly turntabling, live-rendered thumbnail of a library model. */
@Composable
fun ModelPreview(model: LibraryModel, modifier: Modifier = Modifier, spin: Boolean = true, zoom: Float = 1f) {
    // The angle is only read inside the draw lambda, so spinning invalidates drawing, never composition.
    val angle = remember { mutableFloatStateOf(30f) }
    val paused = LocalPreviewsPaused.current
    LaunchedEffect(spin, paused) {
        if (!spin) { angle.floatValue = 30f; return@LaunchedEffect }
        if (paused) return@LaunchedEffect
        // Turned in whole preview frames: equal values don't redraw, so the mesh renders PREVIEW_FPS times a second.
        val step = TURNTABLE_DEG_PER_SEC / PREVIEW_FPS
        var raw = angle.floatValue
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                raw = (raw + (now - last) / 1e9f * TURNTABLE_DEG_PER_SEC) % 360f
                last = now
                angle.floatValue = floor(raw / step) * step
            }
        }
    }
    val items = remember(model) {
        listOf(RenderItem(model.parts, Transform(position = Vec3(0f, model.restHeight - 0.8f, 0f)), model.color, 0))
    }
    val glow = remember(model) { listOf(model.color.copy(alpha = 0.28f), Color.Transparent) }
    Canvas(modifier.semantics { contentDescription = "Model 3D ${model.name}" }) {
        drawCircle(Brush.radialGradient(glow, center, size.minDimension * 0.6f), size.minDimension * 0.6f)
        drawScene(
            items,
            Camera(yaw = angle.floatValue, pitch = 18f, distance = 6.2f, target = Vec3(0f, 0.55f, 0f)),
            showGrid = false,
            zoom = zoom * 0.62f,
        )
    }
}
