package com.example.myapplication.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.engine.Easing
import com.example.myapplication.engine.FxParam
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.Prop
import com.example.myapplication.engine.PropKey
import com.example.myapplication.engine.ease
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Blue
import com.example.myapplication.ui.theme.Ink700
import com.example.myapplication.ui.theme.Ink800
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid

/** How a value is shown: like After Effects, transforms read in pixels, percent and revolutions. */
internal class UiUnit(val toUi: (Float) -> Float, val fromUi: (Float) -> Float, val format: (Float) -> String)

internal fun unitFor(key: PropKey, spec: FxParam, width: Float = 1920f, height: Float = 1080f): UiUnit = when (key) {
    is PropKey.T -> when (key.prop) {
        Prop.AnchorX -> UiUnit({ it * width/2 }, { it * 2/width }) { "%.1f".format(it) }
        Prop.AnchorY -> UiUnit({ it * height/2 }, { it * 2/height }) { "%.1f".format(it) }
        Prop.PosX -> UiUnit({ (it + 1f) * width/2 }, { it * 2/width - 1f }) { "%.1f".format(it) }
        Prop.PosY -> UiUnit({ (it + 1f) * height/2 }, { it * 2/height - 1f }) { "%.1f".format(it) }
        Prop.Scale -> UiUnit({ it * 100f }, { it / 100f }) { "%.1f%%".format(it) }
        Prop.Rotation -> UiUnit({ it }, { it }) { v ->
            val rev = (v / 360f).toInt()
            "%dx %+.1f°".format(rev, v - rev * 360f)
        }
        Prop.Opacity -> UiUnit({ it * 100f }, { it / 100f }) { "%.0f%%".format(it) }
        Prop.PosZ -> UiUnit({ it }, { it }) { "%.2f".format(it) }
        Prop.OrbitX, Prop.OrbitY -> UiUnit({ it }, { it }) { "%.1f°".format(it) }
    }
    is PropKey.Fx -> {
        val pattern = if (spec.max - spec.min > 5f) "%.1f" else "%.2f"
        UiUnit({ it }, { it }) { v -> pattern.format(v) + if (spec.unit.isEmpty()) "" else " ${spec.unit}" }
    }
}

/**
 * One keyframable property, After Effects style: stopwatch, name, scrubby value, keyframe
 * navigator (◀ ◆ ▶) once animated, plus a slim slider for thumbs.
 */
@Composable
internal fun PropertyRow(state: EditorState, layer: Layer, key: PropKey, label: String? = null) {
    val spec = layer.specOf(key) ?: return
    val track = layer.trackOf(key) ?: return
    val name = label ?: spec.label
    val value = track.valueAt(state.time, layer.easing)
    val unit = remember(key, spec, state.settings) { unitFor(key, spec, state.settings.width.toFloat(), state.settings.height.toFloat()) }
    val keyHere = track.hasKeyAt(state.snap(state.time))
    val haptics = LocalHapticFeedback.current
    val tint = if (track.animated) Accent else Blue
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            StopwatchButton(track.animated, name) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.toggleStopwatch(key)
            }
            Text(name, style = MaterialTheme.typography.labelMedium, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            ScrubValue(value, spec.min..spec.max, unit, tint, name) { state.setValue(key, it) }
            if (track.animated) {
                KeyNavigator(
                    keyHere = keyHere,
                    onPrev = { state.jumpKey(key, next = false) },
                    onToggle = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); state.toggleKeyframeHere(key) },
                    onNext = { state.jumpKey(key, next = true) },
                )
            }
        }
        CompactSlider(value, spec.min..spec.max, tint, name) { state.setValue(key, it) }
    }
}

@Composable
private fun StopwatchButton(animated: Boolean, name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tint by animateColorAsState(if (animated) Accent else TextLow, tween(160), label = "stopwatch")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(if(focused) 2.dp else 0.dp,if(focused) Accent else Color.Transparent,CircleShape)
            .onFocusChanged { focused=it.isFocused }
            .semantics { contentDescription="Animasi $name"; stateDescription=if(animated) "Aktif" else "Nonaktif" }
            .clickable(role = Role.Switch, onClickLabel = if (animated) "Matikan animasi $name" else "Animasikan $name", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Timer, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
}

/** Drag sideways to scrub the number, tap to type it, the way After Effects' blue values work. */
@Composable
internal fun ScrubValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: UiUnit,
    color: Color,
    name: String,
    onChange: (Float) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val perPx = (range.endInclusive - range.start) / with(LocalDensity.current) { 280.dp.toPx() }
    Text(
        unit.format(unit.toUi(value)),
        style = MonoLabel.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(if(focused) 2.dp else 0.dp,if(focused) TextHigh else Color.Transparent,RoundedCornerShape(6.dp))
            .onFocusChanged { focused=it.isFocused }
            .testTag("scrub-$name")
            .pointerInput(range) {
                detectHorizontalDragGestures { ch, dx ->
                    ch.consume()
                    change((current + dx * perPx).coerceIn(range.start, range.endInclusive))
                }
            }
            .clickable(onClickLabel = "Ketik nilai $name") { editing = true }
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
    if (editing) {
        var text by remember { mutableStateOf("%.2f".format(unit.toUi(value)).replace(',', '.')) }
        val parsed=text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }
        AlertDialog(
            onDismissRequest = { editing = false },
            modifier=Modifier.onKeyEvent { event ->
                if(event.type==KeyEventType.KeyDown && event.key==Key.Escape) { editing=false; true } else false
            },
            containerColor = Ink800,
            title = { Text(name, style = MaterialTheme.typography.titleMedium, color = TextHigh) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MonoLabel.copy(fontSize = 16.sp, color = TextHigh),
                    isError=parsed==null,
                    supportingText={ if(parsed==null) Text("Masukkan angka yang valid.") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = LineStrong),
                )
            },
            confirmButton = {
                TextButton(enabled=parsed!=null,onClick = {
                    parsed?.let { change(unit.fromUi(it).coerceIn(range.start, range.endInclusive)) }
                    editing = false
                }) { Text("Terapkan", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Batal", color = TextMid) } },
        )
    }
}

@Composable
private fun KeyNavigator(keyHere: Boolean, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val fill by animateColorAsState(if (keyHere) Accent else Color.Transparent, tween(140), label = "keyFill")
    Row(verticalAlignment = Alignment.CenterVertically) {
        NavBox("Keyframe sebelumnya", onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = TextMid, modifier = Modifier.size(18.dp)) }
        NavBox(if (keyHere) "Hapus keyframe" else "Tambah keyframe", onToggle) {
            Canvas(Modifier.size(14.dp)) {
                val r = size.minDimension / 2
                val p = Path().apply { moveTo(r, 0f); lineTo(2 * r, r); lineTo(r, 2 * r); lineTo(0f, r); close() }
                drawPath(p, fill)
                drawPath(p, if (keyHere) Accent else TextMid, style = Stroke(1.8f))
            }
        }
        NavBox("Keyframe berikutnya", onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextMid, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun NavBox(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(width = 44.dp, height = 44.dp).clip(RoundedCornerShape(8.dp))
            .border(if(focused) 2.dp else 0.dp,if(focused) Accent else Color.Transparent,RoundedCornerShape(8.dp))
            .onFocusChanged { focused=it.isFocused }
            .semantics { contentDescription=label }
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A thin slider: tap or drag anywhere on the track. Bipolar ranges fill from zero. */
@Composable
internal fun CompactSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    name: String,
    track: Brush? = null,
    onChange: (Float) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 12.dp)
            .border(if(focused) 2.dp else 0.dp,if(focused) Accent else Color.Transparent,RoundedCornerShape(4.dp))
            .onFocusChanged { focused=it.isFocused }
            .onKeyEvent { event ->
                if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                    Key.DirectionLeft,Key.DirectionDown -> { change((current-span/100).coerceIn(range.start,range.endInclusive)); true }
                    Key.DirectionRight,Key.DirectionUp -> { change((current+span/100).coerceIn(range.start,range.endInclusive)); true }
                    else -> false
                }
            }
            .focusable()
            .semantics {
                contentDescription = name
                progressBarRangeInfo = ProgressBarRangeInfo(current.coerceIn(range.start, range.endInclusive), range)
                setProgress { v -> change(v.coerceIn(range.start, range.endInclusive)); true }
            }
            .pointerInput(range) {
                detectTapGestures { p -> change(range.start + (p.x / size.width).coerceIn(0f, 1f) * span) }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(onDragStart = { p -> change(range.start + (p.x / size.width).coerceIn(0f, 1f) * span) }) { ch, _ ->
                    ch.consume()
                    change(range.start + (ch.position.x / size.width).coerceIn(0f, 1f) * span)
                }
            }
    ) {
        val y = size.height / 2f
        val h = if (track != null) 6.dp.toPx() else 3.dp.toPx()
        val f = ((current - range.start) / span).coerceIn(0f, 1f)
        val zero = if (range.start < 0f && range.endInclusive > 0f) -range.start / span else 0f
        if (track != null) {
            drawRoundRect(track, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        } else {
            drawRoundRect(LineStrong, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
            val a = size.width * minOf(zero, f); val b = size.width * maxOf(zero, f)
            drawRoundRect(color, Offset(a, y - h / 2), Size(b - a, h), CornerRadius(h / 2))
        }
        val x = size.width * f
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
        drawCircle(if (track != null) Color.Black.copy(alpha = 0.35f) else color, 7.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
    }
}

/** Collapsible group like After Effects' twirl-down properties, animated open and closed. */
@Composable
internal fun TwirlCard(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 90f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "twirl")
    Panel(modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Tutup $title" else "Buka $title", onClick = onToggle)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ChevronRight, null, tint = TextMid, modifier = Modifier.size(20.dp).rotate(turn))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = if (dimmed) TextLow else TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MonoLabel, color = TextLow, maxLines = 1)
                }
                trailing()
            }
            AnimatedVisibility(
                expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) { content() }
            }
        }
    }
}

/** A tiny plot of an easing curve, so presets read at a glance. */
@Composable
internal fun EasingGlyph(easing: Easing, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pad = 2.dp.toPx()
        val w = size.width - pad * 2; val h = size.height - pad * 2
        val path = Path()
        for (i in 0..40) {
            val u = i / 40f
            val x = pad + u * w
            val y = pad + h - ease(u, easing) * h * 0.78f - h * 0.11f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx()))
    }
}

/** Selectable chip showing an easing curve and its After Effects name. */
@Composable
internal fun EasingChip(easing: Easing, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent.copy(alpha = 0.16f) else Ink700, tween(160), label = "easeBg")
    val border by animateColorAsState(if (selected) Accent else Color.Transparent, tween(160), label = "easeBorder")
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClickLabel = "Interpolasi ${easing.label}", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EasingGlyph(easing, if (selected) Accent else TextMid, Modifier.size(width = 26.dp, height = 20.dp))
        Text(easing.label, style = MaterialTheme.typography.labelMedium, color = if (selected) TextHigh else TextMid)
    }
}

