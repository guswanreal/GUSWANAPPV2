package com.example.myapplication.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.*
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun MotionAssistantSheet(state:EditorState,onDismiss:()->Unit) {
    val scope=rememberCoroutineScope()
    var title by remember { mutableStateOf("JUDUL KAMU") }
    var subtitle by remember { mutableStateOf("Buat cerita yang bergerak") }
    var seconds by remember { mutableStateOf("6") }
    var style by remember { mutableStateOf(MotionStyle.Cinematic) }
    var depth by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(MotionAiClient.DEFAULT_MODEL) }
    var plan by remember { mutableStateOf<MotionPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Ink850,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).verticalScroll(rememberScrollState()).testTag("motion-assistant-content").padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Pembuat Motion & AI",style=MaterialTheme.typography.titleLarge,color=TextHigh)
            Text("Preset offline membuat layer yang bisa diedit dan diekspor sebagai MP4. Ditambahkan mulai playhead; bisa di-undo.",color=TextMid)
            OutlinedTextField(title,{title=it},label={Text("Judul motion")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(subtitle,{subtitle=it},label={Text("Subjudul")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(seconds,{seconds=it},label={Text("Durasi (detik)")},singleLine=true)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MotionStyle.entries.forEach { option -> FilterChip(selected=style==option,onClick={style=option},label={Text(option.name)}) }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text("Gunakan bidang 3D",color=TextHigh); Switch(depth,{depth=it}) }
            Button(enabled=!busy,onClick={
                try {
                    val requested=seconds.toFloatOrNull() ?: throw IllegalArgumentException("Durasi harus berupa angka.")
                    require(requested.isFinite() && requested>0) { "Durasi harus lebih dari nol." }
                    val chosen=(plan ?: MotionPlan(title,subtitle)).copy(title=title,subtitle=subtitle,seconds=requested,style=style,depth=depth)
                    val added=MotionBuilder.build(chosen,state.layers,state.time,state.duration)
                    state.commit(state.layers+added)
                    state.select(added.firstOrNull { it.name=="Motion title" }?.id)
                    state.panel=EditorPanel.Transform
                    onDismiss()
                } catch(e:IllegalArgumentException) { error=e.message }
            },modifier=Modifier.fillMaxWidth()) { Text("Buat motion di timeline") }

            HorizontalDivider()
            Text("Asisten AI online",style=MaterialTheme.typography.titleMedium,color=TextHigh)
            Text("Instruksi teks dikirim ke Google Gemini. Video tidak diunggah. API key hanya disimpan selama panel ini terbuka. Biaya/kuota mengikuti akun Gemini kamu.",color=TextMid)
            OutlinedTextField(apiKey,{apiKey=it},label={Text("Gemini API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(model,{model=it},label={Text("Model Gemini")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(prompt,{prompt=it},label={Text("Instruksi untuk AI")},placeholder={Text("Buat intro teknologi biru 6 detik dengan flare. Bantu pilih tracking untuk video saya.")},modifier=Modifier.fillMaxWidth())
            Button(enabled=!busy && apiKey.isNotBlank() && prompt.isNotBlank(),onClick={
                busy=true; error=null
                val remaining=state.duration-state.time
                val hasVideo=state.selected?.type==LayerType.Video
                scope.launch {
                    try {
                        val generated=MotionAiClient.plan(apiKey,model,prompt,remaining,hasVideo)
                        plan=generated; title=generated.title; subtitle=generated.subtitle; seconds=generated.seconds.toString(); style=generated.style; depth=generated.depth
                    } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { error=e.message ?: "AI belum dapat dihubungi." }
                    finally { busy=false }
                }
            }) { Text(if(busy) "AI sedang menyusun..." else "Susun rencana dengan AI") }
            plan?.let { result ->
                Text("Rencana: ${result.style.name} · ${result.seconds} detik",color=TextHigh)
                Text(result.advice,color=TextMid)
                Text("Efek: ${result.effects.joinToString { it.label }}",color=TextMid)
                if(state.selected!=null) OutlinedButton(onClick={
                    val selected=state.selected ?: return@OutlinedButton
                    val effects=result.effects.filter { it.appliesTo(selected.type) }
                    var uid=(state.layers.flatMap { it.effects }.maxOfOrNull { it.uid } ?: 1000)+1
                    state.updateSelected { it.copy(effects=it.effects+effects.map { type -> EffectInstance.of(type,uid++) }) }
                    onDismiss()
                }) { Text("Terapkan efek ke layer terpilih") }
                if(result.suggestTracking && state.selected?.type==LayerType.Video) OutlinedButton(onClick={ state.panel=EditorPanel.Tracker; onDismiss() }) { Text("Buka tracking kamera untuk video") }
            }
            error?.let { Text(it,color=Danger) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun CameraTrackingPanel(state:EditorState,layer:Layer) {
    val scope=rememberCoroutineScope()
    var fov by remember(layer.id) { mutableFloatStateOf(60f) }
    var progress by remember(layer.id) { mutableFloatStateOf(0f) }
    var job by remember(layer.id) { mutableStateOf<Job?>(null) }
    var message by remember(layer.id) { mutableStateOf<String?>(null) }
    var running by remember(layer.id) { mutableStateOf(false) }
    DisposableEffect(layer.id) { onDispose { job?.cancel() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Tracking kamera 3D",style=MaterialTheme.typography.titleLarge,color=TextHigh)
        PanelHint("Gunakan video dengan latar diam, detail jelas, dan kamera bergeser. Zoom, kamera diam, atau rotasi saja tidak cukup untuk mengukur kedalaman.")
        PanelHint("Solve eksperimental dengan skala relatif. Membuat Camera 3D dan Tracked Null. Video dijadikan latar yang tidak ikut kamera; layer Mode 3D mengikuti hasil solve.")
        Text("Sudut pandang vertikal: ${fov.toInt()}°",color=TextHigh)
        Slider(fov,{fov=it},valueRange=25f..110f,enabled=!running)
        PanelHint("Sesuaikan FOV dengan kamera sumber. Lensa sangat lebar atau rolling shutter dapat mengurangi akurasi.")
        if(running) LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
        Button(enabled=!running && layer.footage!=null,onClick={
            running=true; progress=0f; message=null
            val captured=layer
            job=scope.launch {
                try {
                    val solve=CameraTracker3D.solve(captured,fov) { progress=it }
                    val current=state.layers.firstOrNull { it.id==captured.id }
                    require(current==captured) { "Layer berubah saat dianalisis. Jalankan solve ulang." }
                    state.applyCameraSolve(captured.id,solve)
                    message="${solve.points.size} titik 3D, ${solve.poses.size} pose. Terselesaikan sampai ${state.timecode(solve.solvedUntil)}. Pilih Tracked Null untuk menempatkan layer 3D."
                    if(solve.solvedUntil<captured.end-2/state.fps.toFloat()) message+=" Tracking terputus; hanya rentang yang valid diterapkan."
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) { message=e.message ?: "Solve belum berhasil." }
                finally { running=false }
            }
        }) { Text("Analisis & buat kamera 3D") }
        if(running) TextButton(onClick={job?.cancel()}) { Text("Batalkan analisis") }
        message?.let { Text(it,color=TextMid) }
    }
}
