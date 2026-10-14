package com.example.myapplication.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.DemoProjects
import com.example.myapplication.engine.ModelLibrary
import com.example.myapplication.engine.Template
import com.example.myapplication.engine.Templates
import com.example.myapplication.engine.ProjectSnapshot
import com.example.myapplication.ui.components.ActionButton
import com.example.myapplication.ui.components.CompositionPreview
import com.example.myapplication.ui.components.LocalPreviewsPaused
import com.example.myapplication.ui.components.ModelPreview
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.components.StatPill
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Ink850
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid

private const val SECTIONS = 5

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenTemplate: (Template) -> Unit,
    onNewVideo: () -> Unit,
    onNewScene: () -> Unit,
    onOpenModel: (String) -> Unit,
    onSeeTemplates: () -> Unit,
    onSeeModels: () -> Unit,
    savedProjects: List<ProjectSnapshot> = emptyList(),
    onResume: (ProjectSnapshot) -> Unit = {},
) {
    // One clock staggers every section in; each reads it in graphicsLayer, so the reveal never recomposes.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(SECTIONS.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    fun Modifier.staggered(index: Int) = graphicsLayer {
        val p = (reveal.value - index * 0.6f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
    }

    val list = rememberLazyListState()
    val templateRow = rememberLazyListState()
    val modelRow = rememberLazyListState()
    // Previews hold still while anything scrolls, so the scroll itself gets every frame.
    val scrolling by remember {
        derivedStateOf { list.isScrollInProgress || templateRow.isScrollInProgress || modelRow.isScrollInProgress }
    }
    // The hero preview is the top of its card: it plays only while no more than a third of the card is scrolled off.
    val heroVisible by remember {
        derivedStateOf {
            val hero = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "new" }
            hero != null && hero.offset > -hero.size / 3
        }
    }

    CompositionLocalProvider(LocalPreviewsPaused provides scrolling) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item { Header(Modifier.staggered(0)) }
            if (savedProjects.isNotEmpty()) item(key = "saved") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Proyek tersimpan", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                    savedProjects.forEach { project ->
                        Panel(Modifier.fillMaxWidth(), onClickLabel = "Lanjutkan ${project.settings.name}", onClick = { onResume(project) }) {
                            Column(Modifier.padding(14.dp)) {
                                Text(project.settings.name, color = TextHigh, style = MaterialTheme.typography.titleMedium)
                                Text("${project.layers.size} layer · ${project.settings.duration.toInt()} detik · Lanjutkan", color = TextMid)
                            }
                        }
                    }
                }
            }
            item(key = "new") {
                CompositionLocalProvider(LocalPreviewsPaused provides (scrolling || !heroVisible)) {
                    NewProjectCard(onCreate = onNewVideo, onStudio = onNewScene, modifier = Modifier.staggered(1))
                }
            }
            item {
                Column(Modifier.staggered(2)) {
                    SectionHeader("Template", Templates.all.size, onSeeTemplates)
                    LazyRow(state = templateRow, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Templates.all, key = { it.id }) { t -> TemplateCard(t) { onOpenTemplate(t) } }
                    }
                }
            }
            item {
                Column(Modifier.staggered(3)) {
                    SectionHeader("Model 3D", ModelLibrary.showcase.size, onSeeModels)
                    LazyRow(state = modelRow, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ModelLibrary.showcase, key = { it.id }) { m ->
                            Panel(Modifier.width(124.dp), onClickLabel = "Buka ${m.name} di Studio 3D", onClick = { onOpenModel(m.id) }) {
                                Column {
                                    ModelPreview(m, Modifier.fillMaxWidth().aspectRatio(1f))
                                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                                        Text(m.name, style = MaterialTheme.typography.titleSmall, color = TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${m.triangleCount} tris", style = MonoLabel, color = TextLow)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { KeyframeHint(Modifier.staggered(4)) }
        }
    }
}

@Composable
private fun Header(modifier: Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text("Guswaneditor", style = MaterialTheme.typography.titleLarge, color = TextHigh, modifier = Modifier.weight(1f))
        StatPill("v1.0", color = TextLow)
    }
}

/** The Guswaneditor logo: an orange tile with a stylised "G" made of a play triangle and an orbit. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Accent), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val w = size.width
            drawArc(Ink950, 40f, 280f, false, style = Stroke(w * 0.14f))
            val tri = Path().apply {
                moveTo(w * 0.42f, w * 0.32f); lineTo(w * 0.72f, w * 0.5f); lineTo(w * 0.42f, w * 0.68f); close()
            }
            drawPath(tri, Ink950)
        }
    }
}

@Composable
private fun NewProjectCard(onCreate: () -> Unit, onStudio: () -> Unit, modifier: Modifier) {
    val template = Templates.all.first()
    val layers = remember { template.layers() }
    Panel(modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                CompositionPreview(layers, Modifier.fillMaxSize(), scrubber = true)
                StatPill("1920×1080 · 30 fps", Modifier.align(Alignment.TopEnd).padding(10.dp), color = TextHigh)
            }
            Column(Modifier.padding(16.dp)) {
                Text("Proyek baru", style = MaterialTheme.typography.headlineSmall, color = TextHigh)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pilih rasio, resolusi 360p sampai 4K, dan frame rate, lalu mulai dari kosong atau template.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Video", onCreate, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                    ActionButton("Scene 3D", onStudio, icon = Icons.Rounded.ViewInAr, modifier = Modifier.weight(1f), primary = false)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MonoLabel, color = TextLow)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSeeAll) { Text("Lihat semua", color = TextMid, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun TemplateCard(t: Template, onClick: () -> Unit) {
    val layers = remember(t.id) { t.layers() }
    Panel(Modifier.width(208.dp), onClickLabel = "Buka ${t.title}", onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                CompositionPreview(layers, Modifier.fillMaxSize(), playing = false, stillTime = 4.2f)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Ink850.copy(alpha = 0.7f))))
                StatPill(formatDuration(DemoProjects.DURATION), Modifier.align(Alignment.BottomEnd).padding(8.dp), color = TextHigh)
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(t.accent))
                    Spacer(Modifier.width(8.dp))
                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                Text("${t.ratio} · ${layers.size} layer", style = MonoLabel, color = TextLow)
            }
        }
    }
}

@Composable
private fun KeyframeHint(modifier: Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Timer, null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Ketuk ikon stopwatch di samping properti untuk merekam keyframe.",
            style = MaterialTheme.typography.bodySmall, color = TextMid,
        )
    }
}

fun formatDuration(seconds: Float): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)
