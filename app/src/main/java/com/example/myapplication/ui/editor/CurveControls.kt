package com.example.myapplication.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.*
import com.example.myapplication.ui.components.Panel
import com.example.myapplication.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurveControls(state: EditorState,layer: Layer,key: PropKey,track: Track,selected: Int?) {
    if(track.keys.size<2) return
    var all by remember(key) { mutableStateOf(true) }
    val segment=(selected ?: 0).coerceAtMost(track.keys.lastIndex-1)
    val a=track.keys[segment]; val b=track.keys[segment+1]
    val handles=segmentHandles(a,b,a.easing ?: layer.easing)
    val spec=layer.specOf(key) ?: return
    val unit=unitFor(key,spec,state.settings.width.toFloat(),state.settings.height.toFloat())
    Panel(Modifier.fillMaxWidth().testTag("curve-controls"),shape=RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Flow curves",style=MaterialTheme.typography.titleMedium,color=TextHigh)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Semua segmen",all) { all=true }
                ChoiceChip("Segmen ${segment+1}",!all) { all=false; state.graphKeyIndex=segment }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                FlowCurves.forEach { curve ->
                    Column(Modifier.width(84.dp).clip(RoundedCornerShape(6.dp)).background(Ink950)
                        .border(1.dp,LineStrong,RoundedCornerShape(6.dp))
                        .clickable(onClickLabel="Terapkan kurva ${curve.name}") { state.applyCurve(key,if(all) null else segment,curve); state.graphKeyIndex=segment }
                        .padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                            val path=Path()
                            for(i in 0..50) {
                                val x=i/50f
                                val y=bezierEase(curve.x1,curve.y1,curve.x2,curve.y2,x)
                                val p=Offset(x*size.width,size.height*(.85f-y*.7f))
                                if(i==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
                            }
                            drawPath(path,Accent,style=Stroke(2.dp.toPx()))
                        }
                        Text(curve.name,style=MaterialTheme.typography.labelSmall,color=TextHigh)
                    }
                }
            }
            PanelHint("Pilih key pada graph untuk menampilkan handle. Seret lingkaran untuk mengatur influence dan speed; angka di bawah bisa diketik.")
            if(selected!=null) {
                val k=track.keys[selected]
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Waktu key",color=TextMid,modifier=Modifier.weight(1f))
                    ScrubValue(k.time,0f..state.duration,UiUnit({it},{it}) { "%.3f s".format(it) },Accent,"Waktu key") { state.moveKey(key,selected,it,k.value) }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Nilai key",color=TextMid,modifier=Modifier.weight(1f))
                    val low=minOf(spec.min,track.keys.minOf { it.value }); val high=maxOf(spec.max,track.keys.maxOf { it.value })
                    ScrubValue(k.value,low..high,unit,Accent,"Nilai key") { state.moveKey(key,selected,k.time,it) }
                }
            }
            handles?.let { (out,incoming) ->
                val factor=abs(unit.toUi(1f)-unit.toUi(0f)).coerceAtLeast(.001f)
                val maxSpeed=maxOf(abs(b.value-a.value)/(b.time-a.time)*8f,abs(out.speed)*2,abs(incoming.speed)*2,.1f)
                listOf(Triple("Out",out,true),Triple("In",incoming,false)).forEach { (name,handle,outgoing) ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("$name influence",color=TextMid,modifier=Modifier.weight(1f))
                        ScrubValue(handle.influence,.001f..1f,UiUnit({it*100},{it/100}) { "%.1f%%".format(it) },Accent,"$name influence") {
                            state.setBezierHandle(key,segment,outgoing,it,handle.speed)
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("$name speed",color=TextMid,modifier=Modifier.weight(1f))
                        ScrubValue(handle.speed,-maxSpeed..maxSpeed,UiUnit({it*factor},{it/factor}) { "%.1f /s".format(it) },Accent,"$name speed") {
                            state.setBezierHandle(key,segment,outgoing,handle.influence,it)
                        }
                    }
                }
            }
        }
    }
}
