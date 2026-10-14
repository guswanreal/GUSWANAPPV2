package com.example.myapplication.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.BlendKind
import com.example.myapplication.engine.Easing
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.LayerType
import com.example.myapplication.engine.ModelLibrary
import com.example.myapplication.engine.Prop
import com.example.myapplication.engine.PropKey
import com.example.myapplication.engine.ShapeKind
import com.example.myapplication.engine.Track
import com.example.myapplication.engine.drawShape
import com.example.myapplication.engine.transformProps
import com.example.myapplication.engine.transformProperties
import com.example.myapplication.engine.supports3D
import com.example.myapplication.ui.components.ActionButton
import com.example.myapplication.ui.components.ColorSwatch
import com.example.myapplication.ui.components.ModelPreview
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.components.ToolButton
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Danger
import com.example.myapplication.ui.theme.Ink700
import com.example.myapplication.ui.theme.Ink750
import com.example.myapplication.ui.theme.LayerColors
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid

@Composable
internal fun PanelHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextLow, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
}

// ---------------------------------------------------------------- Transform

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransformPanel(state: EditorState, layer: Layer) {
    var open by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TwirlCard(
            "Transform",
            if (layer.type == LayerType.Camera) "Point of Interest · Zoom · Roll · Orbit" else "Anchor · Position · Scale · Rotation · Opacity",
            open, { open = !open },
            trailing = {
                ToolButton(Icons.Rounded.RestartAlt, "Reset transform", {
                    state.updateSelected { l -> l.copy(tracks = Prop.entries.associateWith { Track(it.default) }) }
                }, size = 48.dp)
            },
        ) {
            // The engine's list per layer type, so the panel matches what the renderer and keyframes use.
            if (layer.supports3D() && layer.type != LayerType.Camera) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode 3D", color = TextHigh, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = layer.is3D, onCheckedChange = { enabled ->
                        state.updateSelected { it.copy(is3D = enabled) }
                    })
                }
                if (layer.is3D) PanelHint("Putar bidang pada sumbu X/Y, atur kedalaman Z, lalu gerakkan layer Kamera.")
            }
            layer.transformProperties().forEach { PropertyRow(state, layer, PropKey.T(it)) }
            if(layer.type==LayerType.Camera) {
                PanelHint("Hubungkan kamera ke Null 3D melalui Parent untuk menggerakkan seluruh rig. Zoom tetap dikendalikan oleh kamera.")
                ActionButton("Buat Null pengendali kamera",state::createCameraRig,primary=false)
            }
            if (layer.type == LayerType.Model3D) PanelHint("Rotation pada layer 3D memutar model di sumbu Y.")
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Interpolasi default", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                PanelHint("Dipakai key yang belum punya interpolasi sendiri. Atur per key di Graph Editor.")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Easing.entries.forEach { e -> EasingChip(e, layer.easing == e) { state.updateSelected { it.copy(easing = e) } } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Effect Controls

@Composable
internal fun EffectControlsPanel(state: EditorState, layer: Layer, onBrowse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton("Tambah efek", onBrowse, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
        if (layer.effects.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Belum ada efek", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                    PanelHint("Buka Effects & Presets: blur, glow, wave warp, glitch, wipe, dan lainnya. Efek diterapkan berurutan dari atas ke bawah.")
                }
            }
        }
        layer.effects.forEachIndexed { index, e ->
            key(e.uid) {
                var menu by remember { mutableStateOf(false) }
                TwirlCard(
                    title = e.type.label,
                    subtitle = e.type.category.label,
                    expanded = e.uid in state.expandedFx,
                    onToggle = { state.toggleExpanded(e.uid) },
                    dimmed = !e.enabled,
                    modifier = Modifier.alpha(if (e.enabled) 1f else 0.7f),
                    trailing = {
                        FxSwitch(e.enabled, e.type.label) { state.toggleEffect(e.uid) }
                        Box {
                            ToolButton(Icons.Rounded.MoreVert, "Opsi ${e.type.label}", { menu = true }, size = 48.dp)
                            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Ink700) {
                                if (index > 0) DropdownMenuItem(text = { Text("Pindah ke atas") }, onClick = { menu = false; state.moveEffect(e.uid, up = true) })
                                if (index < layer.effects.lastIndex) DropdownMenuItem(text = { Text("Pindah ke bawah") }, onClick = { menu = false; state.moveEffect(e.uid, up = false) })
                                DropdownMenuItem(text = { Text("Reset parameter") }, onClick = { menu = false; state.resetEffect(e.uid) })
                                DropdownMenuItem(text = { Text("Hapus efek", color = Danger) }, onClick = { menu = false; state.removeEffect(e.uid) })
                            }
                        }
                    },
                ) {
                    if (e.type.params.isEmpty()) PanelHint(e.type.description)
                    e.type.params.forEachIndexed { i, p -> PropertyRow(state, layer, PropKey.Fx(e.uid, i), label = p.label) }
                }
            }
        }
    }
}

/** The "fx" switch from After Effects' Effect Controls. */
@Composable
private fun FxSwitch(on: Boolean, name: String, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Accent else Ink700, tween(160), label = "fxBg")
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Switch, onClickLabel = if (on) "Matikan $name" else "Nyalakan $name", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "fx",
            style = MonoLabel,
            color = if (on) Color.Black else TextLow,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------- Colour

private fun toHsv(c: Color) = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }

private val HueBrush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })

@Composable
internal fun ColorPanel(state: EditorState, layer: Layer) {
    var hsv by remember(layer.id) { mutableStateOf(toHsv(layer.color)) }
    // Follow outside changes (swatches, undo) without snapping the hue of greys back to red.
    LaunchedEffect(layer.color) {
        if (Color.hsv(hsv[0], hsv[1], hsv[2]).toArgb() != layer.color.toArgb()) hsv = toHsv(layer.color)
    }
    fun set(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) {
        hsv = floatArrayOf(h, s, v)
        state.setColor(Color.hsv(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)), coalesce = "color-${layer.id}")
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(layer.color).border(1.dp, LineStrong, RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (layer.type == LayerType.Model3D) "Warna glow & tint" else "Warna layer", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                        Text("#%06X".format(layer.color.toArgb() and 0xFFFFFF), style = MonoLabel, color = TextMid)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(LayerColors + listOf(Accent, Color.White, Color(0xFF111114))) { c ->
                        ColorSwatch(c, layer.color.toArgb() == c.toArgb(), { state.setColor(c) }, label = "Warna #%06X".format(c.toArgb() and 0xFFFFFF))
                    }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SliderLabel("Hue", "%.0f°".format(hsv[0]))
                CompactSlider(hsv[0], 0f..360f, Accent, "Hue", track = HueBrush) { set(h = it) }
                SliderLabel("Saturation", "%.0f%%".format(hsv[1] * 100))
                CompactSlider(hsv[1], 0f..1f, Accent, "Saturation", track = Brush.horizontalGradient(listOf(Color.hsv(hsv[0], 0f, hsv[2]), Color.hsv(hsv[0], 1f, hsv[2])))) { set(s = it) }
                SliderLabel("Brightness", "%.0f%%".format(hsv[2] * 100))
                CompactSlider(hsv[2], 0f..1f, Accent, "Brightness", track = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv[0], hsv[1], 1f)))) { set(v = it) }
            }
        }
    }
}

@Composable
private fun SliderLabel(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = TextMid, modifier = Modifier.weight(1f))
        Text(value, style = MonoLabel, color = TextHigh)
    }
}

// ---------------------------------------------------------------- Blending & switches

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlendingPanel(state: EditorState, layer: Layer) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Mode", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                PanelHint("Cara layer bercampur dengan layer di bawahnya.")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlendKind.entries.forEach { b -> ChoiceChip(b.label, layer.blend == b) { state.setBlend(b) } }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) { PropertyRow(state, layer, PropKey.T(Prop.Opacity)) }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                SwitchRow(if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, "Video (tampil)", layer.visible) { state.toggleVisible(layer.id) }
                SwitchRow(Icons.Rounded.FilterCenterFocus, "Solo", layer.solo) { state.toggleSolo(layer.id) }
                SwitchRow(if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Kunci", layer.locked) { state.toggleLock(layer.id) }
            }
        }
    }
}

@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent.copy(alpha = 0.18f) else Ink700, tween(160), label = "chipBg")
    val border by animateColorAsState(if (selected) Accent else Color.Transparent, tween(160), label = "chipBorder")
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) TextHigh else TextMid,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) Accent else TextLow, tween(160), label = "switchTint")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Switch, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextHigh, modifier = Modifier.weight(1f))
        Text(if (on) "ON" else "OFF", style = MonoLabel, color = tint)
    }
}

// ---------------------------------------------------------------- Parent & Link

@Composable
internal fun ParentPanel(state: EditorState, layer: Layer) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            PanelHint("Child mengikuti Position, Anchor, Rotation, dan Scale parent. Tampilan pada playhead tetap saat mengganti atau melepas parent.")
            PanelHint(if(layer.type==LayerType.Camera) "Kamera dapat dihubungkan ke Null. Position, Rotation dan Scale Null menggerakkan rig kamera 3D." else "Gunakan Null Object untuk menggerakkan beberapa layer bersama.")
            state.parentError?.let { Text(it, color = Danger, modifier = Modifier.padding(12.dp)) }
            ParentRow(null, "Tidak ada", Icons.Rounded.LinkOff, null, layer.parentId == null) { state.setParent(null) }
            state.parentCandidates().forEach { p ->
                ParentRow(p.color, p.name, null, p.type.label, layer.parentId == p.id) { state.setParent(p.id) }
            }
        }
    }
}

@Composable
private fun ParentRow(color: Color?, name: String, icon: ImageVector?, type: String?, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent.copy(alpha = 0.12f) else Color.Transparent, tween(160), label = "parentBg")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(bg).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(color))
        if (icon != null) Icon(icon, null, tint = TextMid, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (type != null) Text(type, style = MonoLabel, color = TextLow)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = Accent)
    }
}

// ---------------------------------------------------------------- Layer content

@Composable
internal fun TextPanel(state: EditorState, layer: Layer) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = layer.text,
                onValueChange = { v -> state.updateSelected("text-${layer.id}") { it.copy(text = v) } },
                label = { Text("Isi teks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = LineStrong, focusedLabelColor = Accent),
            )
            OutlinedTextField(
                value = layer.name,
                onValueChange = { v -> state.updateSelected("name-${layer.id}") { it.copy(name = v) } },
                label = { Text("Nama layer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = LineStrong, focusedLabelColor = Accent),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShapePanel(state: EditorState, layer: Layer) {
    Panel(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShapeKind.entries.forEach { k ->
                val active = layer.shape == k
                val border by animateColorAsState(if (active) Accent else Line, tween(160), label = "shapeBorder")
                Column(
                    Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Accent.copy(alpha = 0.12f) else Ink750)
                        .border(1.dp, border, RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton, onClickLabel = k.label) { state.updateSelected { it.copy(shape = k) } }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(40.dp)) {
                        translate(size.width / 2, size.height / 2) { drawShape(k, layer.color, size.minDimension * 0.36f, outline = false) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(k.label, style = MaterialTheme.typography.labelSmall, color = if (active) TextHigh else TextMid)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelPanel(state: EditorState, layer: Layer, importing: Boolean, onImport: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            PanelHint("Model diputar oleh Rotation layer. Model hasil impor muncul di sini juga.")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelTile(null, "Impor file", false, onImport) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.UploadFile, null, tint = if (importing) TextLow else Accent, modifier = Modifier.size(26.dp))
                    }
                }
                ModelLibrary.all.forEach { m ->
                    val active = layer.modelId == m.id
                    ModelTile(m.id, m.name, active, { state.updateSelected { it.copy(modelId = m.id) } }) {
                        ModelPreview(m, Modifier.size(56.dp), spin = active)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelTile(id: String?, name: String, active: Boolean, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val border by animateColorAsState(if (active) Accent else Color.Transparent, tween(160), label = "modelBorder")
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.14f) else Ink750)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(role = if (id == null) Role.Button else Role.RadioButton, onClickLabel = if (id == null) "Impor model 3D" else "Pakai $name", onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        preview()
        Text(name, style = MaterialTheme.typography.labelSmall, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Header shown above every panel: the panel name and the layer it edits. */
@Composable
internal fun PanelTitle(panel: EditorPanel, layer: Layer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(panel.label, style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(layer.color))
        Spacer(Modifier.width(6.dp))
        Text(layer.name, style = MonoLabel, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
    }
}
