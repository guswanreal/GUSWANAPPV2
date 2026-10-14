package com.example.myapplication.engine

data class EffectPreset(val id: String,val name: String,val description: String,val types: List<FxType>) {
    fun appliesTo(type:LayerType)=types.all { it.appliesTo(type) }
    fun build(layer:Layer,start:Float,firstUid:Int):List<EffectInstance> {
        val t=start.coerceIn(layer.start,(layer.end-.02f).coerceAtLeast(layer.start))
        val end=minOf(layer.end,t+.65f)
        fun fade(a:Float,b:Float)=Track(a,listOf(Keyframe(t,a,Easing.EaseOut),Keyframe(end,b)))
        fun pulse(peak:Float)=Track(0f,listOf(Keyframe(t,0f,Easing.EaseOut),Keyframe(t+(end-t)*.25f,peak,Easing.EaseIn),Keyframe(end,0f)))
        return types.mapIndexed { i,type ->
            val uid=firstUid+i
            when {
                id=="rgb-hit" && type==FxType.ChromaticAberration -> EffectInstance.of(type,uid,0 to pulse(18f))
                id=="rgb-hit" && type==FxType.Glitch -> EffectInstance.of(type,uid,0 to pulse(.65f))
                id=="impact" && type==FxType.Shake -> EffectInstance.of(type,uid,0 to pulse(36f),1 to Track(10f),2 to pulse(5f))
                id=="zoom" && type==FxType.RadialBlur -> EffectInstance.of(type,uid,0 to pulse(.8f))
                id=="reveal" && type==FxType.TextAnimator -> EffectInstance.of(type,uid,0 to fade(0f,1f))
                id=="wipe" && type==FxType.LinearWipe -> EffectInstance.of(type,uid,0 to fade(1f,0f))
                id=="film" && type==FxType.Noise -> EffectInstance.of(type,uid,0 to Track(.08f))
                id=="film" && type==FxType.Vignette -> EffectInstance.of(type,uid,0 to Track(.3f))
                id=="cinema" && type==FxType.DeepGlow -> EffectInstance.of(type,uid,0 to Track(28f),1 to Track(.6f))
                id=="handheld" && type==FxType.Shake -> EffectInstance.of(type,uid,0 to Track(8f),1 to Track(1.6f),2 to Track(.7f),3 to Track(.01f))
                else -> EffectInstance.of(type,uid)
            }
        }
    }
}

val EffectPresets=listOf(
    EffectPreset("cinema","Cinema glow","Glow lembut, exposure dan vignette",listOf(FxType.DeepGlow,FxType.Exposure,FxType.Vignette)),
    EffectPreset("impact","Impact shake","Shake yang mereda dengan motion blur",listOf(FxType.Shake,FxType.MotionBlur)),
    EffectPreset("rgb-hit","RGB hit","RGB split dan glitch dengan keyframe pulse",listOf(FxType.ChromaticAberration,FxType.Glitch)),
    EffectPreset("zoom","Zoom burst","Zoom blur pulse dengan tepi berulang",listOf(FxType.MotionTile,FxType.RadialBlur)),
    EffectPreset("reveal","Text reveal","Range selector dengan motion blur",listOf(FxType.TextAnimator,FxType.MotionBlur)),
    EffectPreset("wipe","Quick wipe","Wipe dari playhead ke gambar penuh",listOf(FxType.LinearWipe)),
    EffectPreset("film","Film grain","Grain ringan dan vignette",listOf(FxType.Noise,FxType.Vignette)),
    EffectPreset("vhs","VHS tape","Distorsi pita, scanlines dan noise",listOf(FxType.Vhs,FxType.Noise)),
    EffectPreset("handheld","Handheld rig","Shake halus untuk Null pengendali kamera",listOf(FxType.Shake)),
)

