package com.example.myapplication.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.engine.ProjectSnapshot
import com.example.myapplication.engine.ProjectStore
import com.example.myapplication.engine.EncoderCaps
import com.example.myapplication.engine.FrameRates
import com.example.myapplication.engine.ProjectSettings
import com.example.myapplication.engine.Resolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.myapplication.engine.FootageImporter
import com.example.myapplication.engine.COMPOSITION_TEXT_CACHE
import com.example.myapplication.engine.ImportedModels
import com.example.myapplication.engine.LayerType
import com.example.myapplication.engine.ModelImporter
import com.example.myapplication.engine.ModelLibrary
import com.example.myapplication.engine.ShapeKind
import com.example.myapplication.engine.Template
import com.example.myapplication.engine.VideoExporter
import com.example.myapplication.engine.drawComposition
import com.example.myapplication.engine.drawShape
import com.example.myapplication.engine.pickLayer
import com.example.myapplication.engine.footageToScreen
import com.example.myapplication.ui.components.ActionButton
import com.example.myapplication.ui.components.ModelPreview
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.components.StatPill
import com.example.myapplication.ui.components.ToolButton
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Blue
import com.example.myapplication.ui.theme.Cyan
import com.example.myapplication.ui.theme.Danger
import com.example.myapplication.ui.theme.Ink700
import com.example.myapplication.ui.theme.Ink800
import com.example.myapplication.ui.theme.Ink850
import com.example.myapplication.ui.theme.Ink900
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.Pink
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid
import com.example.myapplication.ui.theme.Violet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

internal fun LayerType.icon(): ImageVector = when (this) {
    LayerType.Text -> Icons.Rounded.TextFields
    LayerType.Shape -> Icons.Rounded.Category
    LayerType.Model3D -> Icons.Rounded.ViewInAr
    LayerType.Gradient -> Icons.Rounded.Gradient
    LayerType.Particles -> Icons.Rounded.Grain
    LayerType.Camera -> Icons.Rounded.Videocam
    LayerType.Video -> Icons.Rounded.Movie
    LayerType.Image -> Icons.Rounded.PhotoLibrary
    LayerType.Null -> Icons.Rounded.FilterCenterFocus
}

private fun EditorPanel.icon(): ImageVector = when (this) {
    EditorPanel.Transform -> Icons.Rounded.OpenWith
    EditorPanel.Effects -> Icons.Rounded.AutoFixHigh
    EditorPanel.Graph -> Icons.Rounded.Timeline
    EditorPanel.Color -> Icons.Rounded.Palette
    EditorPanel.Blending -> Icons.Rounded.Opacity
    EditorPanel.Parent -> Icons.Rounded.Link
    EditorPanel.Text -> Icons.Rounded.TextFields
    EditorPanel.Shape -> Icons.Rounded.Category
    EditorPanel.Model -> Icons.Rounded.ViewInAr
    EditorPanel.Tracker -> Icons.Rounded.FilterCenterFocus
}

private fun EditorPanel.short(): String = when (this) {
    EditorPanel.Transform -> "Transform"
    EditorPanel.Effects -> "Efek"
    EditorPanel.Graph -> "Graph"
    EditorPanel.Color -> "Warna"
    EditorPanel.Blending -> "Blending"
    EditorPanel.Parent -> "Parent"
    EditorPanel.Text -> "Teks"
    EditorPanel.Shape -> "Bentuk"
    EditorPanel.Model -> "Model"
    EditorPanel.Tracker -> "Tracking"
}

/** Panels that make sense for a layer type, in toolbar order. */
private fun panelsFor(type: LayerType): List<EditorPanel> = buildList {
    if (type == LayerType.Null) {
        add(EditorPanel.Transform); add(EditorPanel.Effects); add(EditorPanel.Graph); add(EditorPanel.Parent)
        return@buildList
    }
    if(type==LayerType.Camera) {
        add(EditorPanel.Transform); add(EditorPanel.Graph); add(EditorPanel.Parent)
        return@buildList
    }
    add(EditorPanel.Transform); add(EditorPanel.Effects); add(EditorPanel.Graph)
    when (type) {
        LayerType.Text -> add(EditorPanel.Text)
        LayerType.Shape -> add(EditorPanel.Shape)
        LayerType.Model3D -> add(EditorPanel.Model)
        LayerType.Video -> add(EditorPanel.Tracker)
        else -> Unit
    }
    add(EditorPanel.Color); add(EditorPanel.Blending)
    if (type != LayerType.Gradient) add(EditorPanel.Parent)
}

private fun importMessage(r: ModelImporter.Result): String =
    if (r.sourceTriangles > r.triangles) "${r.model.name}: ${"%,d".format(r.triangles)} segitiga (disederhanakan dari ${"%,d".format(r.sourceTriangles)})"
    else "${r.model.name}: ${"%,d".format(r.triangles)} segitiga"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(settings: ProjectSettings, template: Template?, onBack: () -> Unit,
    restored: ProjectSnapshot? = null, autosaveEnabled: Boolean = true) {
    val context = LocalContext.current
    val store = remember(context) { ProjectStore.get(context) }
    val state = remember(settings, template?.id, restored?.id) {
        EditorState(settings, template, restored, onSave = { if (autosaveEnabled) store.enqueue(it) })
    }
    HighRefreshRate(state.fps)
    var showExport by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showAssistant by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    val largeType=LocalDensity.current.fontScale>=1.5f
    var importing by remember { mutableStateOf(false) }
    var importIntoSelected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.save() }
    DisposableEffect(state, context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.save()
        }
        lifecycle?.addObserver(observer)
        onDispose { state.save(); lifecycle?.removeObserver(observer) }
    }
    val leaveEditor: () -> Unit = {
        state.save()
        scope.launch { if (autosaveEnabled) store.flush(); onBack() }
    }

    BackHandler { if (state.panel != null) state.panel = null else leaveEditor() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            runCatching { ImportedModels.import(context, uris) }
                .onSuccess { r ->
                    if (importIntoSelected && state.selected?.type == LayerType.Model3D) state.updateSelected { it.copy(modelId = r.model.id) }
                    else state.addLayer(LayerType.Model3D, r.model.id)
                    Toast.makeText(context, importMessage(r), Toast.LENGTH_LONG).show()
                }
                .onFailure { e -> Toast.makeText(context, e.message ?: "Gagal mengimpor model", Toast.LENGTH_LONG).show() }
            importing = false
        }
    }
    fun startImport(intoSelected: Boolean) {
        if (importing) return
        importIntoSelected = intoSelected
        importLauncher.launch(arrayOf("*/*"))
    }

    // Video and photo footage: proxies are built once on import, then preview and tracking read those.
    var mediaProgress by remember { mutableFloatStateOf(-1f) }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Export re-reads the original at full quality later, so keep read access beyond this session.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        mediaProgress = 0f
        scope.launch {
            runCatching { FootageImporter.import(context, uri, maxSeconds = state.duration) { p -> scope.launch { mediaProgress = p } } }
                .onSuccess { f -> state.addLayer(if (f.isImage) LayerType.Image else LayerType.Video, footage = f) }
                .onFailure { e -> Toast.makeText(context, e.message ?: "Gagal mengimpor media", Toast.LENGTH_LONG).show() }
            mediaProgress = -1f
        }
    }
    fun startMedia() {
        if (mediaProgress >= 0f) return
        mediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    LaunchedEffect(state.playing) {
        if (!state.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (state.playing) {
            withFrameNanos { now ->
                var t = state.time + (now - last) / 1e9f
                last = now
                if (t >= state.duration) {
                    if (state.loop) t %= state.duration else { t = state.duration; state.playing = false }
                }
                state.time = t
            }
        }
    }

    val panelArea: @Composable (Modifier) -> Unit = { m ->
        PanelSheet(state, m, importing, onBrowse = { showEffects = true }, onImport = { startImport(intoSelected = true) })
    }

    Column(Modifier.fillMaxSize().background(Ink950).imePadding().testTag("editor-workspace").onKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown) false else when {
            event.isCtrlPressed && event.key==Key.Z && event.isShiftPressed -> { state.redo(); true }
            event.isCtrlPressed && event.key==Key.Z -> { state.undo(); true }
            event.key==Key.Spacebar -> { state.playing=!state.playing; true }
            event.key==Key.DirectionLeft -> { state.playing=false; state.seek(state.time-state.frame); true }
            event.key==Key.DirectionRight -> { state.playing=false; state.seek(state.time+state.frame); true }
            event.key==Key.F9 && state.selected!=null -> {
                val key=state.graphKey ?: state.selected!!.animatedKeys.firstOrNull()
                if(key!=null) state.setKeyEasing(key,state.graphKeyIndex,com.example.myapplication.engine.Easing.EaseInOut)
                true
            }
            event.key==Key.Escape && state.panel!=null -> { state.panel=null; true }
            else -> false
        }
    }.focusable()) {
        EditorTopBar(state, leaveEditor, onExport = { state.playing = false; showExport = true }, saveStatus = if (autosaveEnabled) store.status else "")
        Row(Modifier.fillMaxWidth().background(Ink900).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Composition",style=MaterialTheme.typography.labelLarge,color=TextHigh,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${state.settings.width} × ${state.settings.height} · ${state.fps} fps",style=MonoLabel,color=TextMid,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Box {
                ToolButton(Icons.Rounded.Layers,"Pilih layer",{showLayers=true},size=44.dp)
                DropdownMenu(showLayers,{showLayers=false},modifier=Modifier.heightIn(max=360.dp)) {
                    DropdownMenuItem(text={Text("Tidak ada seleksi")},onClick={state.select(null);showLayers=false})
                    state.layers.asReversed().forEach { layer ->
                        DropdownMenuItem(text={Text(layer.name,maxLines=1,overflow=TextOverflow.Ellipsis)},onClick={
                            state.playing=false; state.select(layer.id)
                            if(state.time<layer.start || state.time>layer.end) state.seek(layer.start)
                            showLayers=false
                        })
                    }
                }
            }
            ToolButton(Icons.Rounded.ZoomOutMap,"Tampilkan panduan komposisi",{state.showGuides=!state.showGuides},active=state.showGuides,size=44.dp)
            if(largeType) ToolButton(Icons.Rounded.Timeline,"Buat Motion",{state.playing=false;showAssistant=true},size=44.dp)
            else TextButton(onClick = { state.playing=false; showAssistant=true }) { Text("Buat Motion",color=TextHigh) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
            if (maxWidth >= 1000.dp || (maxWidth>=840.dp && maxHeight>=560.dp)) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            PreviewStage(state, Modifier)
                        }
                        TransportBar(state)
                        TimelineView(state, Modifier.height(280.dp).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { showAdd = true }
                    }
                    Column(Modifier.width(360.dp).fillMaxHeight().background(Ink900)) {
                        EditorToolbar(state, importing, mediaProgress, onAdd = { state.addLayer(it) }, onImport = { startImport(intoSelected = false) }, onMedia = ::startMedia)
                        // On wide screens the side column always shows a panel for the selection.
                        LaunchedEffect(state.selectedId) { if (state.panel == null && state.selectedId != null) state.panel = EditorPanel.Transform }
                        Box(Modifier.weight(1f)) { panelArea(Modifier.fillMaxSize()) }
                    }
                }
            } else if(maxWidth>maxHeight*1.35f) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(.44f).fillMaxHeight().background(Ink900)) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp),contentAlignment=Alignment.Center) {
                            BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                                val previewWidth=minOf(maxWidth,maxHeight*state.settings.aspectValue)
                                PreviewStage(state,Modifier.width(previewWidth).height(previewWidth/state.settings.aspectValue))
                            }
                        }
                        TransportBar(state,compact=true)
                    }
                    Column(Modifier.weight(.56f).fillMaxHeight()) {
                        if(!keyboardVisible) TimelineView(state,if(state.panel==null) Modifier.weight(1f).fillMaxWidth() else Modifier.height(minOf(112.dp,maxHeight*.24f)).fillMaxWidth()) { showAdd=true }
                        if(state.panel!=null) Box(Modifier.weight(1f)) { panelArea(Modifier.fillMaxSize()) }
                        if(!keyboardVisible) EditorToolbar(state,importing,mediaProgress,onAdd={state.addLayer(it)},onImport={startImport(intoSelected=false)},onMedia=::startMedia)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    val maxPreview = this@BoxWithConstraints.maxHeight * if(state.panel==null) .42f else if(largeType) .14f else .28f
                    if(!keyboardVisible) BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=maxPreview).background(Ink900).padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                        val previewWidth=minOf(maxWidth,(maxPreview-20.dp).coerceAtLeast(40.dp)*state.settings.aspectValue)
                        PreviewStage(state,Modifier.width(previewWidth).height(previewWidth/state.settings.aspectValue))
                    }
                    if(!keyboardVisible) TransportBar(state)
                    if(state.panel!=null) {
                        if(!keyboardVisible) TimelineView(state,Modifier.height(if(largeType) minOf(132.dp,maxHeight*.18f) else 132.dp).fillMaxWidth().padding(horizontal=8.dp)) { showAdd=true }
                        Box(Modifier.weight(1f).fillMaxWidth()) { panelArea(Modifier.fillMaxSize()) }
                    } else {
                        TimelineView(state,Modifier.weight(1f).fillMaxWidth().padding(horizontal=8.dp).padding(bottom=8.dp)) { showAdd=true }
                    }
                    EditorToolbar(state, importing, mediaProgress, onAdd = { state.addLayer(it) }, onImport = { startImport(intoSelected = false) }, onMedia = ::startMedia)
                }
            }
        }
    }

    if (showAdd) {
        AddLayerSheet(
            importing = importing,
            onDismiss = { showAdd = false },
            onAdd = { showAdd = false; state.addLayer(it) },
            onImport = { showAdd = false; startImport(intoSelected = false) },
            onMedia = { showAdd = false; startMedia() },
        )
    }
    if (showAssistant) MotionAssistantSheet(state) { showAssistant=false }
    if (showEffects) {
        EffectsBrowserSheet(onDismiss = { showEffects = false },layerType=state.selected?.type ?: LayerType.Shape,onPreset={
            showEffects=false; state.addPreset(it)
        }) { type ->
            showEffects = false
            state.addEffect(type)
        }
    }
    if (showExport) {
        ModalBottomSheet(onDismissRequest = { showExport = false }, containerColor = Ink800) {
            ExportSheet(state) { showExport = false }
        }
    }
}

// ---------------------------------------------------------------- Top bar & transport

@Composable
private fun EditorTopBar(state: EditorState, onBack: () -> Unit, onExport: () -> Unit, saveStatus: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onBack, tint = TextHigh)
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(state.projectName, style = MaterialTheme.typography.titleMedium, color = TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (saveStatus.isNotBlank()) Text(saveStatus, style = MonoLabel, color = TextLow, maxLines = 1)
        }
        ToolButton(Icons.AutoMirrored.Rounded.Undo, "Urungkan", state::undo, enabled = state.canUndo, tint = TextHigh)
        ToolButton(Icons.AutoMirrored.Rounded.Redo, "Ulangi", state::redo, enabled = state.canRedo, tint = TextHigh)
        Spacer(Modifier.width(4.dp))
        ActionButton("Ekspor", onExport, icon = Icons.Rounded.FileUpload, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp))
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TransportBar(state: EditorState,compact:Boolean=false) {
    val screenDp = LocalConfiguration.current.screenWidthDp
    val roomy=!compact && (LocalDensity.current.fontScale>=1.5f || screenDp<350)
    Column(Modifier.fillMaxWidth().background(Ink900)) {
        if(roomy) Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(state.timecode(state.time),style=MonoLabel.copy(fontSize=17.sp,lineHeight=22.sp),color=Accent,modifier=Modifier.weight(1f))
            Text("/ ${state.timecode(state.duration)}",style=MonoLabel,color=TextMid)
        }
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=if(compact) 4.dp else 10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(roomy) Arrangement.Center else Arrangement.Start) {
            if(!roomy) Column(Modifier.weight(1f)) {
                Text(state.timecode(state.time),style=MonoLabel.copy(fontSize=17.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),color=Accent,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(!compact) Text("/ ${state.timecode(state.duration)}",style=MonoLabel,color=TextLow)
            }
            ToolButton(Icons.Rounded.SkipPrevious,"Keyframe sebelumnya",{state.jumpKey(null,next=false)},tint=TextHigh)
            PlayButton(state)
            ToolButton(Icons.Rounded.SkipNext,"Keyframe berikutnya",{state.jumpKey(null,next=true)},tint=TextHigh)
            if(!compact) Row(if(roomy) Modifier else Modifier.weight(1f),horizontalArrangement=Arrangement.End) {
                ToolButton(Icons.Rounded.ZoomOutMap,"Paskan timeline",{state.dpPerSecond=((screenDp/2f-24f)/state.duration).coerceIn(12f,480f)},tint=TextMid)
                ToolButton(Icons.Rounded.Repeat,"Ulang otomatis",{state.loop=!state.loop},active=state.loop,tint=TextMid)
            }
        }
    }
}

@Composable
private fun PlayButton(state: EditorState) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "play")
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Accent)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClickLabel = if (state.playing) "Jeda" else "Putar") {
                if (!state.playing && state.time >= state.duration - 0.01f) state.time = 0f
                state.playing = !state.playing
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            state.playing,
            transitionSpec = { (scaleIn(initialScale = 0.5f) + fadeIn(tween(140))) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut(tween(90))) },
            label = "playIcon",
        ) { playing ->
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Ink950, modifier = Modifier.size(30.dp))
        }
    }
}

// ---------------------------------------------------------------- Preview

/** Tap to select, drag to move, pinch to scale, twist with two fingers to rotate. */
@Composable
private fun PreviewStage(state: EditorState, modifier: Modifier) {
    val measurer = rememberTextMeasurer(COMPOSITION_TEXT_CACHE)
    val density = LocalDensity.current
    val s by rememberUpdatedState(state)
    val selected = state.selected
    Box(
        modifier
            .aspectRatio(state.settings.aspectValue)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, LineStrong, RoundedCornerShape(4.dp))
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .semantics { contentDescription = "Pratinjau komposisi. Ketuk untuk memilih layer, seret untuk memindah, cubit untuk skala, putar dua jari untuk rotasi." }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        s.playing = false
                        s.select(pickLayer(s.layers, s.time, p, size.toSize(), measurer, density))
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        if (s.selected == null) return@detectTransformGestures
                        s.playing = false
                        s.transformSelected(pan, zoom, rotation, size.toSize())
                    }
                }
        ) {
            // Frames snap to the project's rate, so a 24 fps project previews exactly like its export.
            drawComposition(state.layers, state.snap(state.time), measurer, state.selectedId.takeIf { !state.playing }, background = state.settings.background,fps=state.fps)
            selected?.takeIf { !state.playing && it.type==LayerType.Video && it.faceSolve!=null }?.let { video ->
                video.faceSolve?.at(state.time)?.landmarks?.forEach { point ->
                    footageToScreen(state.layers,video.id,state.time,size,point[0],point[1])?.let { p ->
                        drawCircle(Ink950,4.dp.toPx(),p)
                        drawCircle(Accent,2.dp.toPx(),p)
                    }
                }
            }
            // Title-safe guides, like the overlay in AE's composition panel.
            if (!state.playing && state.showGuides) {
                val inset = Offset(size.width * 0.1f, size.height * 0.1f)
                drawRect(Color.White.copy(alpha = 0.1f), inset, Size(size.width - inset.x * 2, size.height - inset.y * 2), style = Stroke(1f))
            }
        }
        AnimatedVisibility(
            selected != null && !state.playing,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            val l = remember { mutableStateOf(selected) }.apply { if (selected != null) value = selected }.value
            if (l != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(l.color))
                    Spacer(Modifier.width(6.dp))
                    Text(l.name, style = MonoLabel, color = TextHigh, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Toolbar

@Composable
private fun EditorToolbar(state: EditorState, importing: Boolean, mediaProgress: Float, onAdd: (LayerType) -> Unit, onImport: () -> Unit, onMedia: () -> Unit) {
    val layer = state.selected
    Column(Modifier.fillMaxWidth().background(Ink900)) {
        HorizontalDivider(color = Line)
        AnimatedContent(
            layer?.id,
            transitionSpec = { (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(90)) },
            label = "toolbar",
        ) { id ->
            val l = state.layers.firstOrNull { it.id == id }
            LazyRow(contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (l == null) {
                    item { ToolCaption("Tambah") }
                    val media = mediaProgress >= 0f
                    item { ToolItem(Icons.Rounded.Movie, if (media) "%d%%".format((mediaProgress * 100).toInt()) else "Media", enabled = !media, onClick = onMedia) }
                    items(LayerType.entries.filter { it != LayerType.Video && it != LayerType.Image }, key = { it.name }) { t -> ToolItem(t.icon(), t.label) { onAdd(t) } }
                    item { ToolItem(Icons.Rounded.UploadFile, if (importing) "Mengimpor" else "Impor 3D", enabled = !importing, onClick = onImport) }
                } else {
                    items(panelsFor(l.type), key = { it.name }) { p ->
                        ToolItem(p.icon(), p.short(), active = state.panel == p) { state.panel = if (state.panel == p) null else p }
                    }
                    item { ToolDivider() }
                    item { ToolItem(Icons.Rounded.ContentCopy, "Duplikat") { state.duplicateSelected() } }
                    item { ToolItem(Icons.Rounded.FlipToFront, "Ke depan") { state.moveSelected(up = true) } }
                    item { ToolItem(Icons.Rounded.FlipToBack, "Ke belakang") { state.moveSelected(up = false) } }
                    item { ToolItem(if (l.visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (l.visible) "Sembunyi" else "Tampil") { state.toggleVisible(l.id) } }
                    item { ToolItem(if (l.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (l.locked) "Buka" else "Kunci") { state.toggleLock(l.id) } }
                    item { ToolItem(Icons.Rounded.DeleteOutline, "Hapus", tint = Danger) { state.deleteSelected() } }
                }
            }
        }
    }
}

@Composable
private fun ToolCaption(text: String) {
    Text(text.uppercase(), style = MonoLabel, color = TextLow, modifier = Modifier.padding(start = 10.dp, end = 4.dp))
}

@Composable
private fun ToolDivider() {
    Box(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 36.dp).background(Line))
}

@Composable
private fun ToolItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = TextMid,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "toolScale")
    val fg by animateColorAsState(
        when { !enabled -> TextLow.copy(alpha = 0.5f); active -> Accent; else -> tint }, tween(160), label = "toolFg",
    )
    val bar by animateFloatAsState(if (active) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "toolBar")
    Column(
        Modifier
            .width(80.dp)
            .heightIn(min = 60.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(if(active) Ink700 else Color.Transparent)
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(width = 18.dp, height = 3.dp).graphicsLayer { scaleX = bar; alpha = bar }.clip(CircleShape).background(Accent))
    }
}

// ---------------------------------------------------------------- Panels

/** The panel surface that slides up over the timeline (phones) or fills the side column (tablets). */
@Composable
private fun PanelSheet(state: EditorState, modifier: Modifier, importing: Boolean, onBrowse: () -> Unit, onImport: () -> Unit) {
    val layer = state.selected
    val panel = state.panel
    val visible = layer != null && panel != null
    // Keep the last content around while the sheet animates away.
    val lastLayer = remember { mutableStateOf(layer) }.apply { if (layer != null) value = layer }
    val lastPanel = remember { mutableStateOf(panel) }.apply { if (panel != null) value = panel }
    AnimatedVisibility(
        visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(140)),
    ) {
        val l = lastLayer.value ?: return@AnimatedVisibility
        val p = lastPanel.value ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        Column(Modifier.fillMaxSize().clip(shape).background(Ink850).border(1.dp, Line, shape).pointerInput(Unit) { detectTapGestures { } }) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(LineStrong))
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(Icons.Rounded.Close, "Tutup panel", { state.panel = null })
                Box(Modifier.weight(1f)) { PanelTitle(p, l) }
            }
            AnimatedContent(
                p,
                transitionSpec = { (fadeIn(tween(200, delayMillis = 40)) + slideInVertically(tween(240)) { it / 14 }) togetherWith fadeOut(tween(90)) },
                label = "panelContent",
            ) { target ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 28.dp)) {
                    PanelContent(state, l, target, importing, onBrowse, onImport)
                }
            }
        }
    }
}

@Composable
private fun PanelContent(state: EditorState, layer: com.example.myapplication.engine.Layer, panel: EditorPanel, importing: Boolean, onBrowse: () -> Unit, onImport: () -> Unit) {
    when (panel) {
        EditorPanel.Transform -> TransformPanel(state, layer)
        EditorPanel.Effects -> EffectControlsPanel(state, layer, onBrowse)
        EditorPanel.Graph -> GraphEditorPanel(state, layer) { state.panel = EditorPanel.Transform }
        EditorPanel.Color -> ColorPanel(state, layer)
        EditorPanel.Blending -> BlendingPanel(state, layer)
        EditorPanel.Parent -> ParentPanel(state, layer)
        EditorPanel.Text -> if (layer.type == LayerType.Text) TextPanel(state, layer) else TransformPanel(state, layer)
        EditorPanel.Shape -> if (layer.type == LayerType.Shape) ShapePanel(state, layer) else TransformPanel(state, layer)
        EditorPanel.Model -> if (layer.type == LayerType.Model3D) ModelPanel(state, layer, importing, onImport) else TransformPanel(state, layer)
        EditorPanel.Tracker -> TrackingPanel(state,layer)
    }
}

// ---------------------------------------------------------------- Add layer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLayerSheet(importing: Boolean, onDismiss: () -> Unit, onAdd: (LayerType) -> Unit, onImport: () -> Unit, onMedia: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink850) {
        Text("Tambah layer", style = MaterialTheme.typography.titleLarge, color = TextHigh, modifier = Modifier.padding(horizontal = 20.dp))
        Text("Layer baru mulai di posisi playhead.", style = MaterialTheme.typography.bodySmall, color = TextLow, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        val entries = listOf(
            LayerType.Text to "Judul, subjudul, caption",
            LayerType.Shape to "Lingkaran, kotak, bintang, cincin",
            LayerType.Model3D to "Objek 3D dari pustaka",
            LayerType.Gradient to "Latar gradien bergerak",
            LayerType.Particles to "Partikel yang melayang",
            LayerType.Camera to "Kamera 3D: orbit, zoom, follow",
            LayerType.Null to "Pengendali transformasi beberapa layer",
        )
        LazyVerticalGrid(
            GridCells.Fixed(2),
            modifier = Modifier.testTag("add-layer-grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "media") {
                AddCard(0, "Video / Foto", "Dari galeri, dengan proxy cepat", onMedia) {
                    Icon(Icons.Rounded.PhotoLibrary, null, tint = Accent, modifier = Modifier.size(30.dp))
                }
            }
            itemsIndexed(entries, key = { _, e -> e.first.name }) { i, (type, desc) ->
                AddCard(i + 1, type.label, desc, { onAdd(type) }) { LayerThumb(type) }
            }
            item(key = "import") {
                AddCard(entries.size + 1, if (importing) "Mengimpor…" else "Impor model 3D", "GLB, glTF, OBJ, STL, PLY", onImport) {
                    Icon(Icons.Rounded.UploadFile, null, tint = Accent, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun AddCard(index: Int, title: String, desc: String, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Panel(
        Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 28.dp.toPx() },
        onClickLabel = "Tambah $title",
        onClick = onClick,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(76.dp).background(Ink950), contentAlignment = Alignment.Center) { thumb() }
            Column(Modifier.padding(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextHigh)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = TextMid, maxLines = 2)
            }
        }
    }
}

/** A small live sample of what each layer type looks like. */
@Composable
private fun LayerThumb(type: LayerType) {
    when (type) {
        LayerType.Camera, LayerType.Video, LayerType.Image, LayerType.Null -> Icon(type.icon(), null, tint = Accent, modifier = Modifier.size(30.dp))
        LayerType.Text -> Text("Aa", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = TextHigh)
        LayerType.Shape -> Canvas(Modifier.size(56.dp)) {
            translate(size.width / 2, size.height / 2) { drawShape(ShapeKind.Star, Pink, size.minDimension * 0.42f, outline = false) }
        }
        LayerType.Model3D -> ModelPreview(ModelLibrary.byId("robot"), Modifier.size(76.dp), spin=false,zoom = 1.7f)
        LayerType.Gradient -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Violet, Ink950, Blue.copy(alpha = 0.6f)))))
        LayerType.Particles -> {
            val t=1.4f
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until 26) {
                    val x = ((i * 37) % 100) / 100f * size.width
                    val y = (((i * 53) % 100) / 100f - t * (0.05f + (i % 5) * 0.02f)).mod(1f) * size.height
                    drawCircle((if (i % 4 == 0) Color.White else Cyan).copy(alpha = 0.4f + 0.6f * ((sin(t * 3 + i) + 1) / 2)), 1.5.dp.toPx() + (i % 3), Offset(x, y))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Export

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSheet(state: EditorState, onClose: () -> Unit) {
    val context = LocalContext.current
    val fontResolver = LocalFontFamilyResolver.current
    val scope = rememberCoroutineScope()
    val project = state.settings
    val caps by produceState<EncoderCaps?>(null) { value = withContext(Dispatchers.Default) { EncoderCaps.get() } }
    var resolution by remember { mutableStateOf(project.resolution) }
    var fps by remember { mutableStateOf(project.fps) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val (w, h) = project.sizeFor(resolution)
    val ok = caps?.supports(w, h, fps) ?: true

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val layers = state.layers
        progress = 0f
        job = scope.launch {
            runCatching {
                VideoExporter.export(context, uri, layers, state.duration, w, h, fps, project.background, fontResolver) { p -> progress = p }
            }.onSuccess {
                Toast.makeText(context, "Video tersimpan", Toast.LENGTH_SHORT).show()
                onClose()
            }.onFailure { e ->
                progress = -1f
                if (e !is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(context, "Ekspor gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        Text("Ekspor video", style = MaterialTheme.typography.titleLarge, color = TextHigh)
        Text("MP4 · H.264 · ${w}×${h} · $fps fps · ${state.duration.toInt()} dtk", style = MonoLabel, color = TextMid)
        Spacer(Modifier.height(16.dp))
        Text("Resolusi", style = MaterialTheme.typography.titleSmall, color = TextHigh)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Resolution.entries.filter { it.short <= project.resolution.short }.forEach { r ->
                val (rw, rh) = project.sizeFor(r)
                ExportChip(r.label, resolution == r, caps?.supports(rw, rh, fps) ?: true) { resolution = r }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Frame rate", style = MaterialTheme.typography.titleSmall, color = TextHigh)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrameRates.filter { it <= project.fps }.forEach { f ->
                ExportChip("$f fps", fps == f, caps?.supports(w, h, f) ?: true) { fps = f }
            }
        }
        if (!ok) {
            Spacer(Modifier.height(8.dp))
            Text("Encoder HP ini tidak sanggup ${w}×${h} @ $fps fps. Turunkan resolusi atau frame rate.", style = MaterialTheme.typography.bodySmall, color = Danger)
        }
        Spacer(Modifier.height(20.dp))
        if (progress >= 0f) {
            val total = (state.duration * fps).toInt()
            Text("Merender frame ${(progress * total).toInt()} / $total", style = MonoLabel, color = TextMid)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Accent, trackColor = Ink700,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { job?.cancel(); progress = -1f }) { Text("Batalkan", color = Danger) }
        } else {
            ActionButton(
                "Pilih lokasi & render",
                { if (ok) launcher.launch("${state.projectName.replace(' ', '_')}_${h}p$fps.mp4") },
                icon = Icons.Rounded.FileUpload, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExportChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent.copy(alpha = 0.18f) else Ink700, tween(160), label = "exportBg")
    Text(
        text,
        style = MonoLabel.copy(fontSize = 13.sp),
        color = when { !enabled -> TextLow.copy(alpha = 0.5f); selected -> TextHigh; else -> TextMid },
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) bg else Ink850)
            .border(1.dp, if (selected && enabled) Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    )
}

/** Asks the display for a mode fast enough for projects above 60 fps, and restores it on exit. */
@Composable
private fun HighRefreshRate(fps: Int) {
    val view = LocalView.current
    DisposableEffect(fps) {
        val window = view.context.findActivity()?.window
        val display = view.display
        val current = display?.mode
        val mode = if (window == null || current == null || fps <= 60) null else display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight && it.refreshRate >= fps - 0.5f }
            .minByOrNull { it.refreshRate }
        if (window == null || mode == null) {
            onDispose { }
        } else {
            val previous = window.attributes.preferredDisplayModeId
            window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
            onDispose { window.attributes = window.attributes.apply { preferredDisplayModeId = previous } }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

