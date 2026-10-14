package com.example.myapplication.engine

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class MotionStyle { Cinematic, Kinetic, Minimal }
data class MotionPlan(
    val title:String, val subtitle:String, val seconds:Float=6f,
    val style:MotionStyle=MotionStyle.Cinematic, val accent:String="#47CFFF", val depth:Boolean=true,
    val effects:List<FxType> = listOf(FxType.DeepGlow), val suggestTracking:Boolean=false,
    val advice:String="",
)

/** Deterministic offline generator. AI only supplies a validated plan, never executable code. */
object MotionBuilder {
    val allowedEffects=setOf(FxType.DeepGlow,FxType.Glow,FxType.OpticalFlares,FxType.MotionBlur,FxType.Shine,FxType.ChromaticAberration)
    fun build(plan:MotionPlan,existing:List<Layer>,start:Float,duration:Float):List<Layer> {
        require(plan.title.isNotBlank()) { "Isi judul video motion." }
        require(plan.seconds.isFinite()) { "Durasi tidak valid." }
        require(start.isFinite() && start>=0f && duration-start>=1f) { "Sisakan minimal 1 detik di timeline atau geser playhead ke awal." }
        val length=plan.seconds.coerceIn(1f,(duration-start).coerceAtMost(60f)); val end=start+length
        val accent=Color(runCatching { android.graphics.Color.parseColor(plan.accent) }.getOrDefault(android.graphics.Color.CYAN))
        var id=(existing.maxOfOrNull { it.id } ?: 0)+1
        var uid=(existing.flatMap { it.effects }.maxOfOrNull { it.uid } ?: 1000)+1
        fun effect(type:FxType)=EffectInstance.of(type,uid++)
        fun anim(vararg values:Pair<Float,Float>)=Track(values.first().second,values.map { Keyframe(start+it.first*length,it.second) })
        val root=Layer(id++,"Motion controller",LayerType.Null,accent,start,end,is3D=plan.depth)
            .withTrack(Prop.Scale,anim(0f to 1f,1f to 1.06f))
        val background=Layer(id++,"Motion background",LayerType.Gradient,accent,start,end,effects=listOf(effect(FxType.Vignette)))
        val ring=Layer(id++,"Motion accent",LayerType.Shape,accent,start,end,parentId=root.id,shape=ShapeKind.Ring,
            is3D=plan.depth,effects=listOf(effect(FxType.Glow)))
            .withTrack(Prop.Scale,anim(0f to .05f,.25f to 1.4f,1f to 1.65f))
            .withTrack(Prop.Opacity,anim(0f to 0f,.18f to .65f,.8f to .5f,1f to 0f))
            .withTrack(Prop.PosZ,Track(.35f))
            .withTrack(Prop.OrbitY,anim(0f to -25f,1f to 20f))
        val title=Layer(id++,"Motion title",LayerType.Text,Color.White,start,end,parentId=root.id,text=plan.title.take(120),is3D=plan.depth,
            effects=plan.effects.filter { it in allowedEffects && it!=FxType.OpticalFlares }.distinct().take(4).map(::effect),
            easing=if(plan.style==MotionStyle.Kinetic) Easing.Back else Easing.EaseInOut)
            .withTrack(Prop.PosY,anim(0f to .3f,.22f to -.05f,.8f to -.05f,1f to -.25f))
            .withTrack(Prop.Scale,anim(0f to .2f,.25f to .65f,1f to .7f))
            .withTrack(Prop.Opacity,anim(0f to 0f,.15f to 1f,.85f to 1f,1f to 0f))
            .withTrack(Prop.Rotation,anim(0f to if(plan.style==MotionStyle.Kinetic) -12f else 0f,.25f to 0f))
            .withTrack(Prop.OrbitX,anim(0f to if(plan.depth) -40f else 0f,.3f to 0f))
        val subtitle=Layer(id++,"Motion subtitle",LayerType.Text,accent,start,end,parentId=root.id,text=plan.subtitle.take(160),is3D=plan.depth)
            .withTrack(Prop.Scale,Track(.24f)).withTrack(Prop.PosY,Track(.32f))
            .withTrack(Prop.Opacity,anim(0f to 0f,.25f to 0f,.45f to 1f,.85f to 1f,1f to 0f))
        val layers=mutableListOf(background,root)
        if(plan.style!=MotionStyle.Minimal) layers+=ring
        layers+=title
        if(plan.subtitle.isNotBlank()) layers+=subtitle
        if(plan.style==MotionStyle.Cinematic || FxType.OpticalFlares in plan.effects) {
            val flare=EffectInstance.of(FxType.OpticalFlares,uid++,1 to anim(0f to -.75f,1f to .75f),2 to Track(-.35f),0 to anim(0f to 0f,.2f to 1.2f,.75f to 1.2f,1f to 0f))
            // Black + Screen preserves underlying layers and provides a real draw for RenderNode.
            layers+=Layer(id++,"Motion optical flare",LayerType.Shape,Color.Black,start,end,shape=ShapeKind.Rect,
                blend=BlendKind.Screen,cameraIgnore=true,effects=listOf(flare)).withTrack(Prop.Scale,Track(10f))
        }
        return layers
    }

    fun decodePlan(json:String,maxDuration:Float):MotionPlan {
        val o=JSONObject(json)
        val title=o.getString("title").trim().take(120)
        require(title.isNotBlank()) { "AI tidak menghasilkan judul yang valid." }
        val seconds=o.getDouble("seconds").toFloat()
        require(seconds.isFinite() && maxDuration>=1f)
        val effects=o.optJSONArray("effects") ?: JSONArray()
        return MotionPlan(title,o.optString("subtitle").take(160),seconds.coerceIn(1f,maxDuration.coerceAtMost(60f)),
            MotionStyle.entries.firstOrNull { it.name==o.optString("style") } ?: MotionStyle.Cinematic,
            o.optString("accent").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#47CFFF",
            o.optBoolean("depth",true),(0 until effects.length()).mapNotNull { i -> allowedEffects.firstOrNull { it.name==effects.optString(i) } }.distinct().take(4),
            o.optBoolean("suggestTracking"),o.optString("advice").take(600))
    }
}

/** User-supplied Gemini credential is kept in UI memory, never bundled in the APK or project. */
object MotionAiClient {
    const val DEFAULT_MODEL="gemini-3.5-flash-lite"
    suspend fun plan(apiKey:String,model:String,prompt:String,availableSeconds:Float,hasVideo:Boolean):MotionPlan=withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank() && model.matches(Regex("[a-zA-Z0-9.-]+"))) { "Isi API key dan nama model Gemini." }
        require(prompt.isNotBlank() && availableSeconds>=1f)
        val instruction="You plan editable motion graphics. Return JSON only with title, subtitle, seconds (1 to ${availableSeconds.coerceAtMost(60f)}), style (Cinematic/Kinetic/Minimal), accent (#RRGGBB), depth (boolean), effects (array of DeepGlow, Glow, OpticalFlares, MotionBlur, Shine, ChromaticAberration), suggestTracking (boolean), advice (Indonesian, max 400 chars). A video is selected: $hasVideo. You cannot see the video. Never claim to have analyzed footage. Suggest camera tracking only when relevant; mention that it needs a static scene with parallax. Do not output scripts. User request follows."
        val body=JSONObject().put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",instruction))))
            .put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",JSONArray().put(JSONObject().put("text",prompt.take(4000))))))
            .put("generationConfig",JSONObject().put("responseMimeType","application/json").put("maxOutputTokens",2048))
        val connection=URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        try {
            connection.requestMethod="POST"; connection.connectTimeout=15000; connection.readTimeout=45000; connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json"); connection.setRequestProperty("x-goog-api-key",apiKey.trim())
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=connection.responseCode
            require(status in 200..299) { "Layanan AI mengembalikan HTTP $status. Periksa key, kuota, dan model." }
            val response=connection.inputStream.bufferedReader().use { reader ->
                val out=StringBuilder(); val buffer=CharArray(4096)
                while(true) { currentCoroutineContext().ensureActive(); val n=reader.read(buffer); if(n<0) break; require(out.length+n<=1024*1024); out.append(buffer,0,n) }
                out.toString()
            }
            val parts=JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val text=(0 until parts.length()).map { parts.getJSONObject(it) }.filterNot { it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            MotionBuilder.decodePlan(text,availableSeconds)
        } finally { connection.disconnect() }
    }
}
