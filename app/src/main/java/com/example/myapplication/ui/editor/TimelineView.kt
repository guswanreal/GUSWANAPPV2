package com.example.myapplication.ui.editor

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.LayerType
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Ink900
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Row geometry shared by drawing and hit-testing, so both always agree. */
private class Geo(width: Float, val pxPerSec: Float, val time: Float, val scrollY: Float, d: Density) {
    val center = width / 2f
    val ruler = with(d) { 26.dp.toPx() }
    val top = ruler + with(d) { 8.dp.toPx() }
    val row = with(d) { 42.dp.toPx() }
    val bar = with(d) { 34.dp.toPx() }
    val handle = with(d) { 12.dp.toPx() }
    val grab = with(d) { 18.dp.toPx() }

    fun x(t: Float) = center + (t - time) * pxPerSec
    fun t(x: Float) = time + (x - center) / pxPerSec
    fun barTop(i: Int) = top + i * row - scrollY
    fun rowAt(y: Float): Int = floor((y - top + scrollY) / row).toInt()
}

/**
 * Alight Motion-style timeline: the playhead stays centred and the clips slide under it.
 * Drag to scrub, pinch to zoom, long-press a clip to move it, drag the white handles to trim.
 */
@Composable
internal fun TimelineView(state: EditorState, modifier: Modifier, onAdd: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    // Every layer type, so a new type (camera, footage) can never miss its clip icon.
    val icons = LayerType.entries.associateWith { rememberVectorPainter(it.icon()) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    val s by rememberUpdatedState(state)
    // Top of the stack first, like After Effects and Alight Motion.
    val rows = state.layers.asReversed()
    val rowsNow by rememberUpdatedState(rows)

    fun geo(width: Float, d: Density) = Geo(width, with(d) { s.dpPerSecond.dp.toPx() }, s.time, scrollY, d)

    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Ink900)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Timeline. Geser untuk memindah waktu, cubit untuk zoom, tahan klip untuk menggeser, seret ujung klip untuk memotong." }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        s.playing = false
                        if (zoom != 1f) s.dpPerSecond = (s.dpPerSecond * zoom).coerceIn(12f, 480f)
                        val g = geo(size.width.toFloat(), this)
                        s.time = (s.time - pan.x / g.pxPerSec).coerceIn(0f, s.duration)
                        val content = g.top + rowsNow.size * g.row + 88.dp.toPx()
                        scrollY = (scrollY - pan.y).coerceIn(0f, max(0f, content - size.height))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val g = geo(size.width.toFloat(), this)
                        if (p.y < g.ruler) { s.playing = false; s.seek(g.t(p.x)); return@detectTapGestures }
                        val layer = rowsNow.getOrNull(g.rowAt(p.y))
                        if (layer == null) { s.select(null); return@detectTapGestures }
                        if (layer.id == s.selectedId) {
                            val k = layer.keyTimes.minByOrNull { abs(g.x(it) - p.x) }
                            if (k != null && abs(g.x(k) - p.x) < 14.dp.toPx()) {
                                s.playing = false; s.time = k
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                return@detectTapGestures
                            }
                        }
                        if (p.x in g.x(layer.start)..g.x(layer.end)) {
                            s.select(layer.id)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else s.select(null)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g = geo(size.width.toFloat(), this)
                        val i = g.rowAt(down.position.y)
                        val layer = rowsNow.getOrNull(i) ?: return@awaitEachGesture
                        val top = g.barTop(i)
                        if (down.position.y !in top..(top + g.bar)) return@awaitEachGesture
                        // Trim handles on the selected clip win over everything else.
                        if (layer.id == s.selectedId) {
                            val edge = when {
                                abs(down.position.x - g.x(layer.start)) < g.grab -> 0
                                abs(down.position.x - g.x(layer.end)) < g.grab -> 1
                                else -> -1
                            }
                            if (edge >= 0) {
                                down.consume()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                var lastX = down.position.x
                                drag(down.id) { ch ->
                                    ch.consume()
                                    val dt = (ch.position.x - lastX) / g.pxPerSec
                                    lastX = ch.position.x
                                    if (edge == 0) s.trim(layer.id, dt, 0f) else s.trim(layer.id, 0f, dt)
                                }
                                return@awaitEachGesture
                            }
                        }
                        if (down.position.x !in g.x(layer.start)..g.x(layer.end)) return@awaitEachGesture
                        val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        s.playing = false
                        s.select(layer.id)
                        var lastX = press.position.x
                        drag(press.id) { ch ->
                            ch.consume()
                            s.shiftLayer(layer.id, (ch.position.x - lastX) / g.pxPerSec)
                            lastX = ch.position.x
                        }
                    }
                }
        ) {
            val g = Geo(size.width, state.dpPerSecond.dp.toPx(), state.time, scrollY, this)
            val d = state.duration

            // Outside the composition's duration.
            drawRect(Color.Black.copy(alpha = 0.4f), Offset(0f, 0f), Size(max(0f, g.x(0f)), size.height))
            drawRect(Color.Black.copy(alpha = 0.4f), Offset(g.x(d), 0f), Size(max(0f, size.width - g.x(d)), size.height))

            // Ruler: label spacing adapts to zoom.
            val major = when { g.pxPerSec >= 60.dp.toPx() -> 1; g.pxPerSec >= 28.dp.toPx() -> 2; else -> 5 }
            val minor = if (g.pxPerSec >= 160.dp.toPx()) state.frame * 5 else major / 2f
            drawRect(Ink950, size = Size(size.width, g.ruler))
            var tick = 0f
            while (tick <= d + 1e-3f) {
                val x = g.x(tick)
                if (x in -10f..size.width + 10f) {
                    val isMajor = abs(tick / major - Math.round(tick / major)) < 1e-3f
                    drawLine(if (isMajor) TextLow else LineStrong, Offset(x, g.ruler * if (isMajor) 0.45f else 0.7f), Offset(x, g.ruler), 1f)
                    if (isMajor) {
                        val label = measurer.measure("${tick.toInt()}s", MonoLabel.copy(color = TextLow, fontSize = 10.sp))
                        drawText(label, topLeft = Offset(x + 3.dp.toPx(), 2.dp.toPx()))
                    }
                }
                tick += minor
            }
            drawLine(Line, Offset(0f, g.ruler), Offset(size.width, g.ruler), 1f)

            clipRect(top = g.ruler) {
                rows.forEachIndexed { i, layer ->
                    val top = g.barTop(i)
                    if (top > size.height || top + g.bar < g.ruler) return@forEachIndexed
                    drawClip(layer, layer.id == state.selectedId, g, top, measurer, icons.getValue(layer.type), state.time, state.frame)
                }
                if (rows.isEmpty()) {
                    val hint = measurer.measure("Ketuk + untuk menambah layer", MonoLabel.copy(color = TextLow))
                    drawText(hint, topLeft = Offset(16.dp.toPx(), g.top + 8.dp.toPx()))
                }
            }

            // Fixed centre playhead with a soft glow.
            drawLine(Accent.copy(alpha = 0.22f), Offset(g.center, 0f), Offset(g.center, size.height), 6.dp.toPx())
            drawLine(Accent, Offset(g.center, 0f), Offset(g.center, size.height), 2.dp.toPx())
            val cap = Path().apply {
                val w = 7.dp.toPx()
                moveTo(g.center - w, 0f); lineTo(g.center + w, 0f); lineTo(g.center + w, g.ruler * 0.55f)
                lineTo(g.center, g.ruler * 0.85f); lineTo(g.center - w, g.ruler * 0.55f); close()
            }
            drawPath(cap, Accent)
        }
        AddFab(Modifier.align(Alignment.BottomEnd).padding(12.dp), onAdd)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClip(
    layer: Layer,
    selected: Boolean,
    g: Geo,
    top: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    icon: androidx.compose.ui.graphics.painter.Painter,
    time: Float,
    frame: Float,
) {
    val left = g.x(layer.start)
    val right = g.x(layer.end)
    if (right < 0f || left > size.width) return
    val radius = CornerRadius(8.dp.toPx())
    val fillAlpha = when { selected -> 0.92f; layer.visible -> 0.30f; else -> 0.12f }
    drawRoundRect(layer.color.copy(alpha = fillAlpha), Offset(left, top), Size(right - left, g.bar), radius)
    drawRoundRect(
        if (selected) Color.White else layer.color.copy(alpha = 0.55f),
        Offset(left, top), Size(right - left, g.bar), radius, style = Stroke(if (selected) 2.dp.toPx() else 1.dp.toPx()),
    )

    // Sticky label: stays readable while the clip's start scrolls off screen.
    val ink = if (selected) Ink950 else if (layer.visible) TextHigh else TextLow
    val labelX = max(left, 0f) + 10.dp.toPx()
    if (labelX < right - 28.dp.toPx()) {
        clipRect(left = left, right = right - 6.dp.toPx()) {
            val iconSize = 14.dp.toPx()
            translate(labelX, top + (g.bar - iconSize) / 2f) {
                with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(ink)) }
            }
            val text = measurer.measure(layer.name, MonoLabel.copy(color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            drawText(text, topLeft = Offset(labelX + iconSize + 6.dp.toPx(), top + (g.bar - text.size.height) / 2f))
        }
    }

    // Keyframes as diamonds; the one under the playhead lights up.
    val cy = top + g.bar / 2f
    val r = if (selected) 5.dp.toPx() else 3.5.dp.toPx()
    for (k in layer.keyTimes) {
        val x = g.x(k)
        if (x < left - r || x > right + r) continue
        val diamond = Path().apply { moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close() }
        val here = abs(k - time) < frame / 2
        drawPath(diamond, if (here) Accent else if (selected) Color.White else layer.color.copy(alpha = 0.9f))
        drawPath(diamond, Ink950, style = Stroke(1.2.dp.toPx()))
    }

    if (selected) {
        val hw = g.handle
        for (edgeX in listOf(left, right - hw)) {
            drawRoundRect(Color.White, Offset(edgeX, top), Size(hw, g.bar), CornerRadius(4.dp.toPx()))
            val mx = edgeX + hw / 2f
            drawLine(Ink950, Offset(mx - 1.5.dp.toPx(), top + g.bar * 0.3f), Offset(mx - 1.5.dp.toPx(), top + g.bar * 0.7f), 1.dp.toPx())
            drawLine(Ink950, Offset(mx + 1.5.dp.toPx(), top + g.bar * 0.3f), Offset(mx + 1.5.dp.toPx(), top + g.bar * 0.7f), 1.dp.toPx())
        }
    }
}

@Composable
private fun AddFab(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "fab")
    Box(
        modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
            .clip(CircleShape)
            .background(Accent)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClickLabel = "Tambah layer", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, contentDescription = "Tambah layer", tint = Ink950, modifier = Modifier.size(28.dp)) }
}
