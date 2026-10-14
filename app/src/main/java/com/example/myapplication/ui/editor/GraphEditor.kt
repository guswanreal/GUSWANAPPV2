package com.example.myapplication.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.Easing
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.PropKey
import com.example.myapplication.engine.Track
import com.example.myapplication.ui.components.ActionButton
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.components.ToolButton
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Blue
import com.example.myapplication.ui.theme.Danger
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** After Effects' Graph Editor for one property: value or speed graph, draggable keys, easing presets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraphEditorPanel(state: EditorState, layer: Layer, onOpenTransform: () -> Unit) {
    val keys = layer.animatedKeys
    if (keys.isEmpty()) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Belum ada properti beranimasi", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                PanelHint("Nyalakan stopwatch pada properti di Transform atau Effect Controls, lalu kurvanya muncul di sini.")
                Spacer(Modifier.height(10.dp))
                ActionButton("Buka Transform", onOpenTransform, icon = Icons.Rounded.Tune, primary = false)
            }
        }
        return
    }
    val key = state.graphKey?.takeIf { it in keys } ?: keys.first()
    val track = layer.trackOf(key) ?: return
    val spec = layer.specOf(key) ?: return
    var speedMode by remember { mutableStateOf(false) }
    val selectedIndex = state.graphKeyIndex?.takeIf { it in track.keys.indices }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(keys, key = { it.toString() }) { k ->
                ChoiceChip(shortLabel(layer, k), k == key) { state.graphKey = k; state.graphKeyIndex = null }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Value Graph", !speedMode) { speedMode = false }
            ChoiceChip("Speed Graph", speedMode) { speedMode = true }
        }
        GraphCanvas(state, layer, key, track, speedMode)

        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val unit = unitFor(key, spec)
                if (selectedIndex != null) {
                    val k = track.keys[selectedIndex]
                    Column(Modifier.weight(1f)) {
                        Text("Key ${selectedIndex + 1} · ${state.timecode(k.time)}", style = MonoLabel, color = TextHigh)
                        Text("${unit.format(unit.toUi(k.value))} · ${(k.easing ?: layer.easing).label}", style = MonoLabel, color = TextMid)
                    }
                    ToolButton(Icons.Rounded.DeleteOutline, "Hapus key", { state.deleteKey(key, selectedIndex) }, tint = Danger, size = 48.dp)
                } else {
                    Text(
                        "Ketuk kotak key untuk memilih. Seret untuk mengubah waktu dan nilai.",
                        style = MaterialTheme.typography.bodySmall, color = TextLow, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(if (selectedIndex != null) "Interpolasi key ${selectedIndex + 1}" else "Interpolasi semua key", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                PanelHint("Mengatur bentuk kurva dari key ini ke key berikutnya.")
                Spacer(Modifier.height(6.dp))
                val current = selectedIndex?.let { track.keys[it].easing ?: layer.easing }
                    ?: track.keys.map { it.easing ?: layer.easing }.distinct().singleOrNull()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Easing.entries.forEach { e -> EasingChip(e, current == e) { state.setKeyEasing(key, selectedIndex, e) } }
                }
                Spacer(Modifier.height(10.dp))
                ChoiceChip(if (track.loop) "loopOut(\"cycle\") · ON" else "loopOut(\"cycle\") · OFF", track.loop) { state.setLoop(key, !track.loop) }
                if (track.loop) PanelHint("Animasi diulang dari key pertama setelah key terakhir lewat.")
            }
        }
    }
}

private fun shortLabel(layer: Layer, key: PropKey): String = when (key) {
    is PropKey.T -> key.prop.label
    is PropKey.Fx -> layer.effects.firstOrNull { it.uid == key.uid }?.let { "${it.type.label}: ${it.type.params[key.param].label}" } ?: "Efek"
}

private val PadLeft = 52.dp
private val PadRight = 14.dp
private val PadTop = 16.dp
private val PadBottom = 24.dp

@Composable
private fun GraphCanvas(state: EditorState, layer: Layer, key: PropKey, track: Track, speedMode: Boolean) {
    val spec = layer.specOf(key) ?: return
    val unit = remember(key, spec) { unitFor(key, spec) }
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val duration = state.duration
    val easing = layer.easing
    // Scale factor from model units to displayed units, for speed in px/s, %/s or °/s.
    val uiPerUnit = abs(unit.toUi(1f) - unit.toUi(0f)).takeIf { it > 0f } ?: 1f

    val valueRange = remember(track, easing) {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (i in 0..120) { val v = track.valueAt(duration * i / 120f, easing); lo = min(lo, v); hi = max(hi, v) }
        track.keys.forEach { lo = min(lo, it.value); hi = max(hi, it.value) }
        if (hi - lo < 1e-4f) { val pad = max(abs(hi) * 0.2f, (spec.max - spec.min) * 0.05f); lo -= pad; hi += pad }
        val pad = (hi - lo) * 0.14f
        (lo - pad)..(hi + pad)
    }
    val speedMax = remember(track, easing) {
        var m = 0f
        for (i in 0..240) m = max(m, speedAt(track, duration * i / 240f, easing) * uiPerUnit)
        max(m * 1.15f, 1e-3f)
    }
    var frozen by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    val range = frozen ?: valueRange

    val s by rememberUpdatedState(state)
    val tr by rememberUpdatedState(track)
    val r by rememberUpdatedState(range)
    val mode by rememberUpdatedState(speedMode)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink950)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Graph editor ${spec.label}. Seret kotak key untuk memindahkannya, seret area kosong untuk memindah playhead." }
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val left = PadLeft.toPx(); val right = size.width - PadRight.toPx()
                    val top = PadTop.toPx(); val bottom = size.height - PadBottom.toPx()
                    val w = right - left; val h = bottom - top
                    val span = (r.endInclusive - r.start).takeIf { it > 0f } ?: 1f
                    fun xOf(t: Float) = left + t / duration * w
                    fun tOf(x: Float) = ((x - left) / w * duration).coerceIn(0f, duration)
                    fun yOf(v: Float) = top + (1f - (v - r.start) / span) * h
                    fun vOf(y: Float) = r.start + (1f - (y - top) / h) * span
                    val hit = if (mode) null else tr.keys.indices.minByOrNull { i ->
                        hypot(xOf(tr.keys[i].time) - down.position.x, yOf(tr.keys[i].value) - down.position.y)
                    }?.takeIf { i -> hypot(xOf(tr.keys[i].time) - down.position.x, yOf(tr.keys[i].value) - down.position.y) < 30.dp.toPx() }
                    if (hit != null) {
                        down.consume()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        s.graphKeyIndex = hit
                        frozen = r
                        val k0 = tr.keys[hit]
                        val grab = down.position - Offset(xOf(k0.time), yOf(k0.value))
                        drag(down.id) { ch ->
                            ch.consume()
                            val p = ch.position - grab
                            s.moveKey(key, hit, tOf(p.x), vOf(p.y).coerceIn(spec.min, spec.max))
                        }
                        frozen = null
                    } else {
                        s.playing = false
                        s.graphKeyIndex = null
                        s.seek(tOf(down.position.x))
                        drag(down.id) { ch -> ch.consume(); s.seek(tOf(ch.position.x)) }
                    }
                }
            }
    ) {
        val left = PadLeft.toPx(); val right = size.width - PadRight.toPx()
        val top = PadTop.toPx(); val bottom = size.height - PadBottom.toPx()
        val w = right - left; val h = bottom - top
        fun xOf(t: Float) = left + t / duration * w
        val lo = if (speedMode) 0f else range.start
        val hi = if (speedMode) speedMax else range.endInclusive
        fun yOf(v: Float) = top + (1f - (v - lo) / (hi - lo)) * h

        // Outside the layer's in/out points.
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(left, top), Size(xOf(layer.start) - left, h))
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(xOf(layer.end), top), Size(right - xOf(layer.end), h))

        val label = MonoLabel.copy(color = TextLow)
        for (i in 0..4) {
            val y = top + h * i / 4f
            drawLine(Line, Offset(left, y), Offset(right, y), 1f)
            val v = hi - (hi - lo) * i / 4f
            val text = if (speedMode) "%.0f/s".format(v) else unit.format(unit.toUi(v))
            val layout = measurer.measure(text.take(9), label)
            drawText(layout, topLeft = Offset(4.dp.toPx(), y - layout.size.height / 2f))
        }
        val step = if (w / duration < 28.dp.toPx()) 2 else 1
        for (sec in 0..duration.toInt() step step) {
            val x = xOf(sec.toFloat())
            drawLine(Line, Offset(x, top), Offset(x, bottom), 1f)
            val layout = measurer.measure("${sec}s", label)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, bottom + 4.dp.toPx()))
        }

        val curve = Path()
        val fill = Path()
        val samples = 220
        for (i in 0..samples) {
            val t = duration * i / samples
            val v = if (speedMode) speedAt(track, t, easing) * uiPerUnit else track.valueAt(t, easing)
            val p = Offset(xOf(t), yOf(v).coerceIn(top - 40f, bottom + 40f))
            if (i == 0) { curve.moveTo(p.x, p.y); fill.moveTo(p.x, bottom); fill.lineTo(p.x, p.y) } else { curve.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
        }
        fill.lineTo(right, bottom); fill.close()
        val color = if (speedMode) Blue else Accent
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = bottom))
        drawPath(curve, color, style = Stroke(2.dp.toPx()))

        val ks = 5.dp.toPx()
        track.keys.forEachIndexed { i, k ->
            val v = if (speedMode) speedAt(track, k.time, easing) * uiPerUnit else k.value
            val c = Offset(xOf(k.time), yOf(v))
            val sel = i == state.graphKeyIndex
            if (sel) drawCircle(Accent.copy(alpha = 0.25f), ks * 2.6f, c)
            drawRect(if (sel) Accent else Color.White, Offset(c.x - ks, c.y - ks), Size(ks * 2, ks * 2))
            drawRect(Ink950, Offset(c.x - ks, c.y - ks), Size(ks * 2, ks * 2), style = Stroke(1.5f))
        }

        val px = xOf(state.time)
        drawLine(Accent.copy(alpha = 0.25f), Offset(px, top), Offset(px, bottom), 6.dp.toPx())
        drawLine(Accent, Offset(px, top), Offset(px, bottom), 1.5.dp.toPx())
        drawLine(LineStrong, Offset(left, bottom), Offset(right, bottom), 1f)
    }
}

private fun speedAt(track: Track, t: Float, easing: Easing): Float {
    val d = 1f / 120f
    return abs(track.valueAt(t + d, easing) - track.valueAt(t - d, easing)) / (2 * d)
}
