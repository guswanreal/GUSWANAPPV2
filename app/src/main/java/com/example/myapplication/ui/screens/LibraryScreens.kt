package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.engine.ImportedModels
import com.example.myapplication.ui.components.ToolButton
import com.example.myapplication.ui.theme.Danger
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.ModelCategory
import com.example.myapplication.engine.ModelLibrary
import com.example.myapplication.engine.Template
import com.example.myapplication.engine.Templates
import com.example.myapplication.ui.components.CompositionPreview
import com.example.myapplication.ui.components.LocalPreviewsPaused
import com.example.myapplication.ui.components.ActionButton
import com.example.myapplication.ui.components.ModelPreview
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.components.SelectChip
import com.example.myapplication.ui.components.StatPill
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Ink750
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TextHigh)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid)
    }
}

@Composable
fun TemplatesScreen(contentPadding: PaddingValues, onOpen: (Template) -> Unit) {
    val grid = rememberLazyGridState()
    // One live preview at a time, the first card fully on screen; the others show their still frame.
    val live by remember {
        derivedStateOf {
            val info = grid.layoutInfo
            val cards = info.visibleItemsInfo.filter { it.key is String }
            (cards.firstOrNull { it.offset.y >= info.viewportStartOffset && it.offset.y + it.size.height <= info.viewportEndOffset } ?: cards.firstOrNull())?.key
        }
    }
    CompositionLocalProvider(LocalPreviewsPaused provides grid.isScrollInProgress) {
        TemplateGrid(grid, live, contentPadding, onOpen)
    }
}

@Composable
private fun TemplateGrid(grid: LazyGridState, live: Any?, contentPadding: PaddingValues, onOpen: (Template) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader("Template", "${Templates.all.size} template. Ganti teks, model, dan warnanya di editor.")
        }
        items(Templates.all, key = { it.id }) { t ->
            val layers = remember(t.id) { t.layers() }
            Panel(onClickLabel = "Gunakan template ${t.title}", onClick = { onOpen(t) }) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        CompositionPreview(layers, Modifier.fillMaxSize(), playing = t.id == live, stillTime = 4.2f, scrubber = t.id == live)
                        Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatPill(t.ratio, color = TextHigh)
                            StatPill("${layers.size} layer", color = TextHigh)
                        }
                    }
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(t.accent))
                                Spacer(Modifier.width(8.dp))
                                Text(t.title, style = MaterialTheme.typography.titleMedium, color = TextHigh)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = TextMid, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(12.dp))
                        ActionButton("Pakai", { onOpen(t) }, icon = Icons.Rounded.Edit, primary = false)
                    }
                }
            }
        }
    }
}

@Composable
fun ModelsScreen(contentPadding: PaddingValues, onOpenModel: (String) -> Unit, onNewScene: () -> Unit) {
    var category by rememberSaveable { mutableStateOf<ModelCategory?>(null) }
    var importing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            runCatching { ImportedModels.import(context, uris) }
                .onSuccess { r ->
                    category = ModelCategory.Imported
                    Toast.makeText(context, "${r.model.name} masuk ke pustaka (${"%,d".format(r.triangles)} segitiga)", Toast.LENGTH_LONG).show()
                }
                .onFailure { e -> Toast.makeText(context, e.message ?: "Gagal mengimpor model", Toast.LENGTH_LONG).show() }
            importing = false
        }
    }
    val models = ModelLibrary.all.filter { category == null || it.category == category }
    val grid = rememberLazyGridState()
    CompositionLocalProvider(LocalPreviewsPaused provides grid.isScrollInProgress) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    ScreenHeader("Model 3D", "${ModelLibrary.all.size} model bawaan. Ketuk untuk membuka di Studio 3D.")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SelectChip("Semua", category == null, { category = null }) }
                        items(ModelCategory.entries) { c -> SelectChip(c.label, category == c, { category = c }) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel(onClick = onNewScene) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Accent),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Add, null, tint = Ink950) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Scene kosong", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                            Text("Susun primitif, atur transform dan material", style = MaterialTheme.typography.bodySmall, color = TextMid)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextLow)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel(onClickLabel = "Impor model 3D", onClick = { if (!importing) importLauncher.launch(arrayOf("*/*")) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Ink750), contentAlignment = Alignment.Center) {
                            if (importing) CircularProgressIndicator(color = Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            else Icon(Icons.Rounded.UploadFile, null, tint = Accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (importing) "Mengimpor…" else "Impor model 3D", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                            Text("GLB, glTF, OBJ+MTL, STL, PLY. Dari Blender: Export glTF 2.0 (.glb)", style = MaterialTheme.typography.bodySmall, color = TextMid)
                        }
                    }
                }
            }
            items(models, key = { it.id }) { m ->
                Panel(onClickLabel = "Buka ${m.name}", onClick = { onOpenModel(m.id) }) {
                    Column {
                        Box {
                            ModelPreview(m, Modifier.fillMaxWidth().aspectRatio(1f))
                            StatPill(m.category.label, Modifier.padding(8.dp), color = TextHigh)
                        }
                        Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleSmall, color = TextHigh)
                                Text("${m.parts.size} bagian · ${m.triangleCount} tris", style = MonoLabel, color = TextLow)
                            }
                            if (m.category == ModelCategory.Imported) {
                                ToolButton(Icons.Rounded.DeleteOutline, "Hapus ${m.name}", { scope.launch { ImportedModels.delete(context, m.id) } }, tint = Danger, size = 40.dp)
                            } else {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(m.color))
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp).background(Color.Transparent)) }
        }
    }
}
