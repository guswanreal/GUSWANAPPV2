package com.example.myapplication.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.AspectRatio
import com.example.myapplication.engine.DefaultBackground
import com.example.myapplication.engine.EncoderCaps
import com.example.myapplication.engine.FrameRates
import com.example.myapplication.engine.ProjectSettings
import com.example.myapplication.engine.Resolution
import com.example.myapplication.engine.Template
import com.example.myapplication.ui.components.ActionButton
import com.example.myapplication.ui.components.ColorSwatch
import com.example.myapplication.ui.components.CompositionPreview
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Ink700
import com.example.myapplication.ui.theme.Ink850
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Durations = listOf(5f, 10f, 15f, 30f, 60f)
private val Backgrounds = listOf(DefaultBackground, Color.Black, Color(0xFF1F1F23), Color.White, Color(0xFF0E3B2E), Color(0xFF3B0E2A), Color(0xFF0E2340))

/**
 * Alight Motion's "new project" dialog: aspect ratio, resolution (360p–4K), frame rate and
 * duration. Options the phone can't export or display are disabled with the reason shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewProjectSheet(template: Template?, onDismiss: () -> Unit, onCreate: (ProjectSettings) -> Unit) {
    val view = LocalView.current
    // Frame rates above 60 only make sense where the panel refreshes that fast.
    val maxHz = remember(view) { view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f }
    val caps by produceState<EncoderCaps?>(null) { value = withContext(Dispatchers.Default) { EncoderCaps.get() } }
    val rates = FrameRates.filter { it <= 60 || it <= maxHz + 0.5f }

    var settings by remember {
        mutableStateOf(ProjectSettings(name = template?.title ?: "Proyek baru"))
    }
    fun supported(s: ProjectSettings) = caps?.supports(s.width, s.height, s.fps) ?: true

    /** After a change, fall back to the best combination the encoder accepts. */
    fun choose(next: ProjectSettings) {
        var s = next
        if (!supported(s)) {
            s = Resolution.entries.reversed().map { s.copy(resolution = it) }.firstOrNull { it.resolution.short <= next.resolution.short && supported(it) }
                ?: Resolution.entries.reversed().map { s.copy(resolution = it) }.firstOrNull(::supported) ?: s
        }
        settings = s
    }
    // Once the encoder table loads, make sure the default selection is one this phone can export.
    LaunchedEffect(caps) { if (caps != null) choose(settings) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink850, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Proyek baru", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    Text(if (template != null) "Dari template ${template.title}" else "Komposisi kosong", style = MaterialTheme.typography.bodySmall, color = TextLow)
                }
            }
            Spacer(Modifier.height(14.dp))
            FramePreview(settings, template)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = settings.name,
                onValueChange = { settings = settings.copy(name = it.take(40)) },
                label = { Text("Nama proyek") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = LineStrong, focusedLabelColor = Accent),
            )

            Section("Rasio aspek")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AspectRatio.entries, key = { it.name }) { a ->
                    AspectChip(a, settings.aspect == a) { choose(settings.copy(aspect = a)) }
                }
            }

            Section("Resolusi", settings.width.toString() + "×" + settings.height)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Resolution.entries.forEach { r ->
                    val candidate = settings.copy(resolution = r)
                    OptionChip(r.label, settings.resolution == r, enabled = supported(candidate), why = "Encoder HP tidak sanggup") { choose(candidate) }
                }
            }

            Section("Frame rate", "${settings.fps} fps")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rates.forEach { f ->
                    val candidate = settings.copy(fps = f)
                    OptionChip("$f", settings.fps == f, enabled = supported(candidate), why = "Encoder HP tidak sanggup") { choose(candidate) }
                }
            }
            Text(
                if (maxHz >= 89.5f) "Layar HP ini sampai ${maxHz.toInt()} Hz, jadi ${rates.last()} fps tersedia."
                else "Layar HP ini ${maxHz.toInt()} Hz. Opsi 90 dan 120 fps muncul di HP dengan layar 90/120 Hz.",
                style = MaterialTheme.typography.bodySmall, color = TextLow, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            Section("Durasi", "${settings.duration.toInt()} dtk")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Durations.forEach { d -> OptionChip("${d.toInt()} dtk", settings.duration == d) { settings = settings.copy(duration = d) } }
            }

            Section("Warna latar")
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                items(Backgrounds) { c -> ColorSwatch(c, settings.background == c, { settings = settings.copy(background = c) }, label = "Latar #%06X".format(c.value.toLong().ushr(32).toInt() and 0xFFFFFF)) }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "MP4 · H.264 · ${settings.width}×${settings.height} · ${settings.fps} fps · ${settings.duration.toInt()} dtk",
                style = MonoLabel, color = TextMid, modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                "Buat proyek", { onCreate(settings.copy(name = settings.name.ifBlank { "Proyek baru" })) },
                icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextHigh, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MonoLabel, color = Accent)
    }
}

/** The comp frame morphs to the chosen ratio; with a template it plays inside, showing how it adapts. */
@Composable
private fun FramePreview(settings: ProjectSettings, template: Template?) {
    val ratio by animateFloatAsState(settings.aspectValue, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "ratio")
    val layers = remember(template?.id) { template?.layers() }
    BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(Ink950), contentAlignment = Alignment.Center) {
        val maxW = maxWidth - 24.dp
        val maxH = maxHeight - 24.dp
        val w = if (maxW / ratio <= maxH) maxW else maxH * ratio
        val h = w / ratio
        Box(Modifier.size(w, h).clip(RoundedCornerShape(6.dp)).background(settings.background).border(1.dp, LineStrong, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (layers != null) {
                CompositionPreview(layers, Modifier.fillMaxSize())
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val g = Color.White.copy(alpha = 0.08f)
                    for (i in 1..2) {
                        drawLine(g, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1f)
                        drawLine(g, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
                    }
                }
            }
            Text(
                "${settings.width}×${settings.height}\n${settings.fps} fps",
                style = MonoLabel, color = TextHigh,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AspectChip(a: AspectRatio, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent.copy(alpha = 0.16f) else Ink700, tween(160), label = "aspectBg")
    val border by animateColorAsState(if (selected) Accent else Color.Transparent, tween(160), label = "aspectBorder")
    Column(
        Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClickLabel = "Rasio ${a.label}", onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val k = size.minDimension / maxOf(a.w, a.h)
            val rw = a.w * k; val rh = a.h * k
            drawRoundRect(
                if (selected) Accent else TextMid,
                Offset((size.width - rw) / 2, (size.height - rh) / 2), Size(rw, rh),
                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(1.8.dp.toPx()),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(a.label, style = MaterialTheme.typography.labelLarge, color = if (selected) TextHigh else TextMid)
        Text(a.hint, style = MonoLabel, color = TextLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, enabled: Boolean = true, why: String? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent.copy(alpha = 0.18f) else Ink700, tween(160), label = "optBg")
    val border by animateColorAsState(if (selected) Accent else Color.Transparent, tween(160), label = "optBorder")
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) bg else Ink850)
            .border(1.dp, if (enabled) border else LineStrong, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { if (!enabled && why != null) stateDescription = why }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MonoLabel.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = when { !enabled -> TextLow.copy(alpha = 0.6f); selected -> TextHigh; else -> TextMid },
        )
    }
}
