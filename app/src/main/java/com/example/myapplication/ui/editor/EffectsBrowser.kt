package com.example.myapplication.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.COMPOSITION_TEXT_CACHE
import com.example.myapplication.engine.EffectInstance
import com.example.myapplication.engine.FxCategory
import com.example.myapplication.engine.FxType
import com.example.myapplication.engine.Keyframe
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.LayerType
import com.example.myapplication.engine.Prop
import com.example.myapplication.engine.ShapeKind
import com.example.myapplication.engine.Track
import com.example.myapplication.engine.EffectPreset
import com.example.myapplication.engine.EffectPresets
import com.example.myapplication.engine.drawComposition
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Blue
import com.example.myapplication.ui.theme.Ink850
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid

/** After Effects' "Effects & Presets" panel as a searchable sheet with rendered previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EffectsBrowserSheet(onDismiss: () -> Unit, layerType:LayerType=LayerType.Shape, onPreset:((EffectPreset)->Unit)?=null, onPick: (FxType) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink850, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        var query by remember { mutableStateOf("") }
        var category by remember { mutableStateOf<FxCategory?>(null) }
        var presets by remember { mutableStateOf(false) }
        val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
        val keyboard=LocalSoftwareKeyboardController.current
        val measurer = rememberTextMeasurer(COMPOSITION_TEXT_CACHE)
        val q = query.trim()
        val matches = FxType.entries.filter { t ->
            t.appliesTo(layerType) && (category == null || t.category == category) &&
                (q.isEmpty() || t.label.contains(q, true) || t.description.contains(q, true) || t.category.label.contains(q, true))
        }
        Column(Modifier.fillMaxHeight(0.92f)) {
            if(!keyboardVisible) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Effects & Presets", style = MaterialTheme.typography.titleLarge, color = TextHigh, modifier = Modifier.weight(1f))
                Text("${FxType.entries.count { it.appliesTo(layerType) }} efek", style = MonoLabel, color = TextLow)
            }
            if(onPreset!=null && !keyboardVisible) Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Efek",!presets) { presets=false }
                ChoiceChip("Preset stack",presets) { presets=true }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}),
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextMid) },
                placeholder = { Text("Cari: blur, glow, wipe, glitch…", color = TextLow) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = LineStrong),
            )
            if(!presets && !keyboardVisible) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ChoiceChip("Semua", category == null) { category = null } }
                items(FxCategory.entries, key = { it.name }) { c -> ChoiceChip(c.label, category == c) { category = c } }
            }
            LazyColumn(modifier=Modifier.testTag("effects-list"),contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
                if(presets && onPreset!=null) {
                    val found=EffectPresets.filter { it.appliesTo(layerType) && (q.isEmpty() || it.name.contains(q,true) || it.description.contains(q,true) || it.types.any { t -> t.label.contains(q,true) }) }
                    items(found,key={it.id}) { preset ->
                        var focused by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().heightIn(min=80.dp)
                            .border(if(focused) 2.dp else 0.dp,if(focused) TextHigh else androidx.compose.ui.graphics.Color.Transparent)
                            .onFocusChanged { focused=it.isFocused }
                            .clickable(onClickLabel="Tambah preset ${preset.name}") { onPreset(preset) }.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name,style=MaterialTheme.typography.titleMedium,color=TextHigh)
                                Text(preset.description,style=MaterialTheme.typography.bodySmall,color=TextMid)
                                Text(preset.types.joinToString { it.label },style=MaterialTheme.typography.labelSmall,color=TextLow)
                            }
                            Icon(Icons.Rounded.Add,null,tint=Accent)
                        }
                    }
                    if(found.isEmpty()) item { Text("Preset tidak ditemukan. Ubah kata pencarian.",color=TextMid,modifier=Modifier.padding(20.dp)) }
                } else matches.groupBy { it.category }.forEach { (cat, types) ->
                    item(key = "h-${cat.name}") {
                        Text(cat.label, style = MaterialTheme.typography.labelLarge, color = TextMid, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
                    }
                    items(types, key = { it.name }) { t -> EffectRow(t, measurer) { onPick(t) } }
                }
                if (!presets && matches.isEmpty()) {
                    item { Text("Tidak ada efek yang cocok dengan \"$q\".", style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.padding(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EffectRow(type: FxType, measurer: TextMeasurer, onClick: () -> Unit) {
    val sample = remember(type) { sampleLayers(type) }
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(if(focused) 2.dp else 0.dp,if(focused) TextHigh else androidx.compose.ui.graphics.Color.Transparent)
            .onFocusChanged { focused=it.isFocused }
            .clickable(role = Role.Button, onClickLabel = "Tambah ${type.label}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp))) {
            drawComposition(sample, 2.4f, measurer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(type.label, style = MaterialTheme.typography.titleSmall, color = TextHigh)
            Text(type.description, style = MaterialTheme.typography.bodySmall, color = TextMid, maxLines = 2)
        }
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Accent)
    }
}

/** A star moving across a gradient, with the effect applied, frozen at one frame. */
private fun sampleLayers(type: FxType): List<Layer> = listOf(
    Layer(1, "bg", LayerType.Gradient, Blue, 0f, 10f),
    Layer(
        2, "sample", LayerType.Shape, Accent, 0f, 10f,
        tracks = mapOf(
            Prop.PosX to Track(0f, listOf(Keyframe(0f, -0.7f), Keyframe(4f, 0.35f))),
            Prop.Scale to Track(1.1f),
            Prop.Rotation to Track(0f, listOf(Keyframe(0f, -60f), Keyframe(4f, 30f))),
        ),
        shape = ShapeKind.Star,
        effects = listOf(EffectInstance.of(type, 1)),
    ),
)
