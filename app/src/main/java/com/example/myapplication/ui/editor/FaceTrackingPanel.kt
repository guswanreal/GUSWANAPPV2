package com.example.myapplication.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.FaceTracker3D
import com.example.myapplication.engine.Layer
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun TrackingPanel(state: EditorState, layer: Layer) {
    var face by remember(layer.id) { mutableStateOf(true) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Face 3D",face) { face=true }
            ChoiceChip("Camera solve",!face) { face=false }
        }
        if(face) FaceTrackingPanel(state,layer) else CameraTrackingPanel(state,layer)
    }
}

@Composable
private fun FaceTrackingPanel(state: EditorState, layer: Layer) {
    val scope=rememberCoroutineScope()
    var smoothing by remember(layer.id) { mutableFloatStateOf(.08f) }
    var progress by remember(layer.id) { mutableFloatStateOf(0f) }
    var running by remember(layer.id) { mutableStateOf(false) }
    var error by remember(layer.id) { mutableStateOf<String?>(null) }
    var job by remember(layer.id) { mutableStateOf<Job?>(null) }
    DisposableEffect(layer.id) { onDispose { job?.cancel() } }
    Column(Modifier.testTag("face-tracking-panel"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Tracking wajah 3D",style=MaterialTheme.typography.titleLarge,color=TextHigh)
        Text("Posisi, pitch, yaw dan roll menjadi keyframe pada Face Null 3D. Analisis berjalan offline pada video yang kamu impor.",color=TextMid,style=MaterialTheme.typography.bodyMedium)
        PanelHint("Kedalaman Z diestimasi dari perubahan ukuran wajah. Hasil ini bukan ukuran jarak asli atau rekonstruksi face mesh.")
        Text("Smoothing: ${(smoothing*1000).toInt()} ms",color=TextHigh)
        Slider(smoothing,{smoothing=it},valueRange=0f.. .25f,enabled=!running)
        PanelHint("Mulai dari wajah terbesar, lalu ikuti wajah yang sama. Saat wajah hilang terlalu lama, analisis berhenti pada rentang yang valid.")
        layer.faceSolve?.let {
            Text("Hasil tersimpan: ${it.poses.size} pose, ${(it.coverage*100).toInt()}% frame terdeteksi.",color=TextHigh)
        }
        if(running) {
            Text("Menganalisis wajah: ${(progress*100).toInt()}%",color=TextHigh)
            LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={job?.cancel()}) { Text("Batalkan tracking") }
        }
        Button(enabled=!running && layer.footage!=null,onClick={
            val captured=layer
            running=true; error=null; progress=0f; state.playing=false
            job=scope.launch {
                try {
                    val result=FaceTracker3D.solve(captured,smoothing) { progress=it }
                    require(state.layers.firstOrNull { it.id==captured.id }==captured) { "Video berubah selama analisis. Jalankan tracking ulang." }
                    state.applyFaceSolve(captured.id,result)
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) { error=e.message ?: "Tracking gagal. Periksa video lalu coba lagi." }
                finally { running=false }
            }
        },modifier=Modifier.fillMaxWidth().testTag("start-face-tracking")) { Text("Analisis wajah & buat Null 3D") }
        PanelHint("Setelah analisis, hubungkan teks, shape, atau foto ke Face Null 3D lewat Parent. Aktifkan Abaikan kamera pada attachment bila ada kamera komposisi.")
        error?.let { Text(it,color=Danger,modifier=Modifier.padding(vertical=4.dp)) }
    }
}
