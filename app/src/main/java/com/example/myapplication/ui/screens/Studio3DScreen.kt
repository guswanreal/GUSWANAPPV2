package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.rememberCoroutineScope
import com.example.myapplication.engine.ImportedModels
import com.example.myapplication.engine.ModelImporter
import com.example.myapplication.ui.components.ActionButton
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.Camera
import com.example.myapplication.engine.HitPoly
import com.example.myapplication.engine.LibraryModel
import com.example.myapplication.engine.ModelLibrary
import com.example.myapplication.engine.Part
import com.example.myapplication.engine.RenderItem
import com.example.myapplication.engine.ShadingMode
import com.example.myapplication.engine.Transform
import com.example.myapplication.engine.Vec3
import com.example.myapplication.engine.drawAxisGizmo
import com.example.myapplication.engine.drawScene
import com.example.myapplication.engine.pick
import com.example.myapplication.engine.toObj
import com.example.myapplication.ui.components.ColorSwatch
import com.example.myapplication.ui.components.ModelPreview
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.components.PropertySlider
import com.example.myapplication.ui.components.SelectChip
import com.example.myapplication.ui.components.StatPill
import com.example.myapplication.ui.components.ToolButton
import com.example.myapplication.ui.theme.Amber
import com.example.myapplication.ui.theme.Blue
import com.example.myapplication.ui.theme.Cyan
import com.example.myapplication.ui.theme.Danger
import com.example.myapplication.ui.theme.Emerald
import com.example.myapplication.ui.theme.Ink700
import com.example.myapplication.ui.theme.Ink750
import com.example.myapplication.ui.theme.Ink800
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.Lime
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.Pink
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid
import com.example.myapplication.ui.theme.Violet

data class SceneObject(
    val id: Int,
    val name: String,
    val parts: List<Part>,
    val color: Color,
    val transform: Transform,
) {
    val triangleCount: Int get() = parts.sumOf { it.mesh.triangleCount }
}

enum class TransformTab(val label: String) { Move("Posisi"), Rotate("Rotasi"), Scale("Skala") }

class StudioState(initialModelId: String?) {
    val objects = mutableStateListOf<SceneObject>()
    var selectedId by mutableStateOf<Int?>(null)
    var yaw by mutableFloatStateOf(35f)
    var pitch by mutableFloatStateOf(22f)
    var distance by mutableFloatStateOf(8f)
    var shading by mutableStateOf(ShadingMode.Shaded)
    var showGrid by mutableStateOf(true)
    var autoRotate by mutableStateOf(false)
    var tab by mutableStateOf(TransformTab.Move)
    private var nextId by mutableIntStateOf(1)

    init {
        if (initialModelId != null) {
            add(ModelLibrary.byId(initialModelId), atOrigin = true)
        } else {
            add(ModelLibrary.byId("cube"), atOrigin = true)
        }
    }

    val selected: SceneObject? get() = objects.firstOrNull { it.id == selectedId }
    var target by mutableStateOf(Vec3(0f, 0.8f, 0f))
    val camera: Camera get() = Camera(yaw, pitch, distance, target)

    fun add(model: LibraryModel, atOrigin: Boolean = false) {
        val slot = objects.size
        val x = if (atOrigin) 0f else listOf(2.6f, -2.6f, 0f, 2.6f, -2.6f)[slot % 5]
        val z = if (atOrigin) 0f else listOf(0f, 0f, -2.6f, -2.6f, -2.6f)[slot % 5]
        val obj = SceneObject(nextId++, model.name, model.parts, model.color, Transform(position = Vec3(x, model.restHeight, z)))
        objects += obj
        selectedId = obj.id
    }

    fun update(transform: (SceneObject) -> SceneObject) {
        val i = objects.indexOfFirst { it.id == selectedId }
        if (i >= 0) objects[i] = transform(objects[i])
    }

    fun duplicate() {
        val s = selected ?: return
        val copy = s.copy(id = nextId++, name = "${s.name} (salin)", transform = s.transform.copy(position = s.transform.position + Vec3(1.2f, 0f, 1.2f)))
        objects += copy
        selectedId = copy.id
    }

    fun delete() {
        objects.removeAll { it.id == selectedId }
        selectedId = objects.lastOrNull()?.id
    }

    fun resetCamera() { yaw = 35f; pitch = 22f; distance = 8f; target = Vec3(0f, 0.8f, 0f) }

    /** Blender's View Selected: orbit around the selection and fit it in view. */
    fun frameSelected() {
        val s = selected ?: return
        target = s.transform.position
        val extent = maxOf(s.transform.scale.x, s.transform.scale.y, s.transform.scale.z)
        distance = (4.5f * extent + 2.5f).coerceIn(3f, 22f)
    }

    /** Two-finger pan slides the orbit target across the view plane. */
    fun pan(dx: Float, dy: Float) {
        val k = distance * 0.0016f
        val yr = Math.toRadians(yaw.toDouble())
        val right = Vec3(cos(yr).toFloat(), 0f, -sin(yr).toFloat())
        target = target + right * (-dx * k) + Vec3(0f, dy * k, 0f)
    }
}

private val MaterialColors = listOf(
    Pink, Blue, Violet, Cyan, Emerald, Lime, Amber, Danger,
    Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF334155), Color(0xFF8B5A2B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Studio3DScreen(initialModelId: String?, onBack: () -> Unit) {
    val state = remember(initialModelId) { StudioState(initialModelId) }
    var showLibrary by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            runCatching { ImportedModels.import(context, uris) }
                .onSuccess { r ->
                    state.add(r.model)
                    state.frameSelected()
                    Toast.makeText(context, importSummary(r), Toast.LENGTH_LONG).show()
                }
                .onFailure { e -> Toast.makeText(context, e.message ?: "Gagal mengimpor model", Toast.LENGTH_LONG).show() }
            importing = false
        }
    }
    val startImport = { if (!importing) importLauncher.launch(arrayOf("*/*")) }
    BackHandler(onBack = onBack)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/obj")) { uri ->
        if (uri != null) {
            val obj = state.objects.map { RenderItem(it.parts, it.transform, it.color, it.id) }.toObj()
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(obj.toByteArray()) } }
                .onSuccess { Toast.makeText(context, "Scene diekspor sebagai .obj", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Gagal mengekspor: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(state.autoRotate) {
        if (!state.autoRotate) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now -> state.yaw += (now - last) / 1e9f * 25f; last = now }
    }

    Column(Modifier.fillMaxSize().background(Ink950)) {
        StudioTopBar(
            onBack = onBack,
            onImport = startImport,
            onExport = { exportLauncher.launch("guswan-scene.obj") },
            objectCount = state.objects.size,
            importing = importing,
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 760.dp) {
                Row(Modifier.fillMaxSize()) {
                    Viewport(state, Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, bottom = 12.dp))
                    Column(
                        Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { Inspector(state) { showLibrary = true } }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Viewport(state, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp))
                    Column(
                        Modifier.fillMaxWidth().height(330.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { Inspector(state) { showLibrary = true } }
                }
            }
        }
    }

    if (importing) ImportOverlay()

    if (showLibrary) {
        ModalBottomSheet(onDismissRequest = { showLibrary = false }, containerColor = Ink800) {
            Text("Tambah dari pustaka", style = MaterialTheme.typography.titleLarge, color = TextHigh, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(460.dp),
            ) {
                item(key = "import") {
                    Panel(color = Ink750, onClickLabel = "Impor model 3D", onClick = { showLibrary = false; startImport() }) {
                        Column(Modifier.fillMaxWidth().aspectRatio(0.8f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.UploadFile, null, tint = Accent, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Impor file", style = MaterialTheme.typography.labelMedium, color = TextHigh)
                            Text("GLB · OBJ · STL", style = MonoLabel, color = TextLow)
                        }
                    }
                }
                items(ModelLibrary.all, key = { it.id }) { m ->
                    Panel(
                        Modifier.clickable(role = Role.Button, onClickLabel = "Tambahkan ${m.name}") { state.add(m); showLibrary = false },
                        color = Ink750,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ModelPreview(m, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(m.name, style = MaterialTheme.typography.labelMedium, color = TextHigh, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(onBack: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, objectCount: Int, importing: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack, tint = TextHigh)
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text("Studio 3D", style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Text("$objectCount objek", style = MonoLabel, color = TextLow)
        }
        ActionButton(
            if (importing) "Mengimpor" else "Impor", onImport, icon = Icons.Rounded.UploadFile,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        )
        Spacer(Modifier.width(6.dp))
        ActionButton(
            ".OBJ", onExport, icon = Icons.Rounded.FileDownload, primary = false,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        )
        Spacer(Modifier.width(8.dp))
    }
}

/** Blender's viewport statistics overlay. */
@Composable
private fun SceneStats(state: StudioState, modifier: Modifier) {
    val sel = state.selected
    val verts = state.objects.sumOf { o -> o.parts.sumOf { it.mesh.vertices.size } }
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(sel?.name ?: "Scene Collection", style = MonoLabel, color = if (sel != null) Accent else TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
        StatLine("Objects", "${if (sel != null) 1 else 0}/${state.objects.size}")
        StatLine("Vertices", "%,d".format(verts))
        StatLine("Triangles", "%,d".format(state.objects.sumOf { it.triangleCount }))
    }
}

@Composable
private fun StatLine(key: String, value: String) {
    Row {
        Text(key, style = MonoLabel, color = TextLow, modifier = Modifier.width(76.dp))
        Text(value, style = MonoLabel, color = TextHigh)
    }
}

@Composable
private fun Viewport(state: StudioState, modifier: Modifier) {
    val hits = remember { mutableListOf<HitPoly>() }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Ink800, Ink950)))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Viewport 3D. Geser untuk memutar kamera, cubit untuk zoom, ketuk objek untuk memilih." }
                .pointerInput(Unit) {
                    // Blender-style navigation: one finger orbits, two fingers pan and pinch to zoom.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moving = false
                        var travel = 0f
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            if (!moving) {
                                travel += pan.getDistance() + abs(1f - zoom) * 200f
                                moving = travel > viewConfiguration.touchSlop
                            }
                            if (moving) {
                                if (fingers >= 2) {
                                    state.pan(pan.x, pan.y)
                                    state.distance = (state.distance / zoom).coerceIn(2f, 30f)
                                } else {
                                    state.yaw += pan.x * 0.35f
                                    state.pitch = (state.pitch + pan.y * 0.3f).coerceIn(-10f, 88f)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p -> state.selectedId = pick(hits, p) }
                }
        ) {
            drawScene(
                state.objects.map { RenderItem(it.parts, it.transform, it.color, it.id) },
                state.camera,
                mode = state.shading,
                selectedTag = state.selectedId,
                showGrid = state.showGrid,
                hits = hits,
            )
            drawAxisGizmo(state.camera, Offset(size.width - 44.dp.toPx(), 44.dp.toPx()), 22.dp.toPx())
        }

        SceneStats(state, Modifier.align(Alignment.TopStart).padding(12.dp))

        // Shading mode + view tools, like the header of Blender's viewport.
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ShadingMode.entries.forEach { m ->
                    val active = state.shading == m
                    Text(
                        m.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Ink950 else TextMid,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable(role = Role.RadioButton) { state.shading = m }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(4.dp),
        ) {
            ToolButton(Icons.Rounded.Autorenew, "Putar otomatis", { state.autoRotate = !state.autoRotate }, active = state.autoRotate, tint = TextHigh)
            ToolButton(Icons.Rounded.GridOn, "Tampilkan grid", { state.showGrid = !state.showGrid }, active = state.showGrid, tint = TextHigh)
            ToolButton(Icons.Rounded.FilterCenterFocus, "Fokus ke objek terpilih", state::frameSelected, enabled = state.selected != null, tint = TextHigh)
            ToolButton(Icons.Rounded.CenterFocusStrong, "Reset kamera", state::resetCamera, tint = TextHigh)
        }
    }
}

@Composable
private fun Inspector(state: StudioState, onOpenLibrary: () -> Unit) {
    // Add primitives
    Panel {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tambah objek", style = MaterialTheme.typography.titleSmall, color = TextHigh, modifier = Modifier.weight(1f))
                SelectChip("Pustaka", false, onOpenLibrary, icon = Icons.Rounded.Widgets)
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ModelLibrary.primitives, key = { it.id }) { m ->
                    Column(
                        Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink750)
                            .clickable(role = Role.Button, onClickLabel = "Tambah ${m.name}") { state.add(m) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ModelPreview(m, Modifier.size(48.dp), spin = false, zoom = 1.3f)
                        Text(m.name, style = MaterialTheme.typography.labelSmall, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // Outliner
    Panel {
        Column(Modifier.padding(12.dp)) {
            Text("Outliner", style = MaterialTheme.typography.titleSmall, color = TextHigh)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.objects, key = { it.id }) { o ->
                    SelectChip(o.name, o.id == state.selectedId, { state.selectedId = o.id }, color = o.color)
                }
            }
        }
    }

    val sel = state.selected
    AnimatedContent(sel != null, label = "inspector") { hasSelection ->
        if (!hasSelection || sel == null) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.OpenWith, null, tint = TextLow, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Ketuk objek di viewport untuk mengeditnya", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TransformPanel(state, sel)
                MaterialPanel(state, sel)
            }
        }
    }
}

@Composable
private fun TransformPanel(state: StudioState, sel: SceneObject) {
    Panel {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Transform, null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Transform", style = MaterialTheme.typography.titleSmall, color = TextHigh, modifier = Modifier.weight(1f))
                ToolButton(Icons.Rounded.Refresh, "Reset transform", {
                    state.update { it.copy(transform = Transform(position = Vec3(0f, it.transform.position.y, 0f))) }
                }, size = 40.dp)
                ToolButton(Icons.Rounded.ContentCopy, "Duplikat", state::duplicate, size = 40.dp)
                ToolButton(Icons.Rounded.DeleteOutline, "Hapus", state::delete, tint = Danger, size = 40.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink750).padding(4.dp),
            ) {
                TransformTab.entries.forEach { t ->
                    val active = state.tab == t
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) TextHigh else TextMid,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Ink700 else Color.Transparent)
                            .clickable(role = Role.Tab) { state.tab = t }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val tr = sel.transform
            val axisColors = listOf(Color(0xFFEF4444), Lime, Blue)
            val axisNames = listOf("X", "Y", "Z")
            for (axis in 0..2) {
                val (value, range, fmt) = when (state.tab) {
                    TransformTab.Move -> Triple(tr.position.get(axis), -5f..5f, { v: Float -> "%.2f m".format(v) })
                    TransformTab.Rotate -> Triple(tr.rotation.get(axis), -180f..180f, { v: Float -> "%.0f°".format(v) })
                    TransformTab.Scale -> Triple(tr.scale.get(axis), 0.1f..3f, { v: Float -> "%.2f×".format(v) })
                }
                PropertySlider(
                    "${state.tab.label} ${axisNames[axis]}", value, range,
                    onChange = { v ->
                        state.update {
                            val t = it.transform
                            it.copy(
                                transform = when (state.tab) {
                                    TransformTab.Move -> t.copy(position = t.position.with(axis, v))
                                    TransformTab.Rotate -> t.copy(rotation = t.rotation.with(axis, v))
                                    TransformTab.Scale -> t.copy(scale = t.scale.with(axis, v))
                                }
                            )
                        }
                    },
                    format = fmt, color = axisColors[axis],
                    leading = {
                        Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(axisColors[axis]))
                    },
                )
            }
            if (state.tab == TransformTab.Scale) {
                PropertySlider(
                    "Skala seragam", (tr.scale.x + tr.scale.y + tr.scale.z) / 3f, 0.1f..3f,
                    onChange = { v -> state.update { it.copy(transform = it.transform.copy(scale = Vec3(v, v, v))) } },
                    format = { "%.2f×".format(it) }, color = Violet,
                )
            }
        }
    }
}

@Composable
private fun MaterialPanel(state: StudioState, sel: SceneObject) {
    Panel {
        Column(Modifier.padding(12.dp)) {
            Text("Material", style = MaterialTheme.typography.titleSmall, color = TextHigh)
            Text("Warna dasar objek (bagian berwarna khusus tetap dipertahankan)", style = MaterialTheme.typography.bodySmall, color = TextLow)
            Spacer(Modifier.height(8.dp))
            LazyRow {
                items(MaterialColors) { c ->
                    ColorSwatch(c, sel.color == c, { state.update { it.copy(color = c) } }, label = "Warna material")
                }
            }
        }
    }
}

private fun Vec3.get(axis: Int) = when (axis) { 0 -> x; 1 -> y; else -> z }
private fun Vec3.with(axis: Int, v: Float) = when (axis) { 0 -> copy(x = v); 1 -> copy(y = v); else -> copy(z = v) }

private fun importSummary(r: ModelImporter.Result): String =
    if (r.sourceTriangles > r.triangles) "${r.model.name}: ${"%,d".format(r.triangles)} segitiga (disederhanakan dari ${"%,d".format(r.sourceTriangles)})"
    else "${r.model.name}: ${"%,d".format(r.triangles)} segitiga"

@Composable
private fun ImportOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Panel(color = Ink800) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Mengimpor model", style = MaterialTheme.typography.titleSmall, color = TextHigh)
                    Text("Membaca mesh dan mengoptimalkan", style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            }
        }
    }
}
