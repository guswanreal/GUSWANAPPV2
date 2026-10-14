package com.example.myapplication.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class ProjectSnapshot(
    val id: String,
    val settings: ProjectSettings,
    val layers: List<Layer>,
    val time: Float = 0f,
    val selectedId: Int? = null,
    val savedAt: Long = System.currentTimeMillis(),
)

/** One atomic file per project, plus the previous valid revision for recovery. */
class ProjectStore(private val directory: File) {
    private val writer = Executors.newSingleThreadExecutor()
    private val pending = LinkedHashMap<String, ProjectSnapshot>()
    private var running = false
    private var revision = 0L
    private val main = Handler(Looper.getMainLooper())
    var status by mutableStateOf("Autosave siap")
        private set

    private fun file(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return File(directory, "$id.json")
    }

    fun enqueue(snapshot: ProjectSnapshot) {
        synchronized(pending) {
            pending[snapshot.id] = snapshot
            revision++
            main.post { status = "Menyimpan..." }
            if (running) return
            running = true
        }
        writer.execute {
            while (true) {
                // Coalesce gesture snapshots before doing JSON encoding and disk writes.
                Thread.sleep(120)
                val next = synchronized(pending) {
                    val key = pending.keys.firstOrNull()
                    if (key == null) { running = false; null } else pending.remove(key)!! to revision
                } ?: break
                val error = runCatching { save(next.first) }.exceptionOrNull()
                main.post {
                    synchronized(pending) {
                        if (revision == next.second && pending.isEmpty())
                            status = if (error == null) "Tersimpan otomatis" else "Gagal menyimpan: ${error.message}"
                    }
                }
            }
        }
    }

    /** Barrier for leaving the editor; pending snapshots continue across composable disposal. */
    suspend fun flush() = withContext(Dispatchers.IO) { writer.submit {}.get() }

    @Synchronized fun save(snapshot: ProjectSnapshot) {
        check(directory.isDirectory || directory.mkdirs()) { "Penyimpanan proyek tidak tersedia" }
        val target = file(snapshot.id)
        val atomic = AtomicFile(target)
        val previous = runCatching { atomic.readFully() }.getOrNull()
        if (previous != null && runCatching { ProjectCodec.decode(String(previous, Charsets.UTF_8)) }.isSuccess) {
            writeAtomic(AtomicFile(File(directory, "${snapshot.id}.previous")), previous)
        }
        writeAtomic(atomic, ProjectCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
    }

    @Synchronized fun load(id: String): ProjectSnapshot? {
        val target = file(id)
        return sequenceOf(target, File(directory, "$id.previous")).mapNotNull { path ->
            runCatching { ProjectCodec.decode(String(AtomicFile(path).readFully(), Charsets.UTF_8)) }.getOrNull()
        }.firstOrNull()
    }

    @Synchronized fun list(): List<ProjectSnapshot> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" || it.extension == "previous" || it.name.endsWith(".json.bak") }
        .map { it.name.removeSuffix(".bak").substringBeforeLast('.') }.distinct()
        .mapNotNull { runCatching { load(it) }.getOrNull() }.sortedByDescending { it.savedAt }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Throwable) { file.failWrite(stream); throw e }
    }

    companion object {
        @Volatile private var instance: ProjectStore? = null
        fun get(context: Context): ProjectStore = instance ?: synchronized(this) {
            instance ?: ProjectStore(File(context.applicationContext.filesDir, "projects")).also { instance = it }
        }
    }
}

/** Names, rather than enum ordinals, keep old projects compatible when effects are added. */
object ProjectCodec {
    private fun arr(values: Iterable<*>): JSONArray = JSONArray().apply { values.forEach { put(it) } }
    private fun floats(values: FloatArray) = arr(values.map { if (it.isFinite()) it else JSONObject.NULL })
    private fun JSONArray.floats() = FloatArray(length()) { if (isNull(it)) Float.NaN else getDouble(it).toFloat() }
    private fun JSONObject.f(name: String, default: Float = 0f) = optDouble(name, default.toDouble()).toFloat()
    private fun JSONObject.id(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null
    private inline fun <reified T : Enum<T>> enum(name: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback
    private fun track(t: Track) = JSONObject().put("static", t.static).put("loop", t.loop).put("keys", arr(t.keys.map {
        JSONObject().put("time", it.time).put("value", it.value).put("easing", it.easing?.name)
            .put("inInfluence", it.inInfluence).put("inSpeed", it.inSpeed).put("outInfluence", it.outInfluence).put("outSpeed", it.outSpeed)
    }))
    private fun track(o: JSONObject): Track {
        val keys = o.optJSONArray("keys") ?: JSONArray()
        return Track(o.f("static"), (0 until keys.length()).map {
            val k = keys.getJSONObject(it)
            Keyframe(k.f("time"), k.f("value"), if (k.has("easing")) enum(k.getString("easing"), Easing.Linear) else null,
                k.f("inInfluence", 1f/3), k.f("inSpeed"), k.f("outInfluence", 1f/3), k.f("outSpeed"))
        }.sortedBy { it.time }, o.optBoolean("loop"))
    }

    fun encode(p: ProjectSnapshot): String {
        val s = p.settings
        return JSONObject().put("schema", 1).put("id", p.id).put("savedAt", p.savedAt).put("time", p.time)
            .put("selectedId", p.selectedId).put("settings", JSONObject().put("name", s.name).put("aspect", s.aspect.name)
                .put("resolution", s.resolution.name).put("fps", s.fps).put("duration", s.duration).put("background", s.background.toArgb()))
            .put("layers", arr(p.layers.map(::layer))).toString()
    }

    fun decode(json: String): ProjectSnapshot {
        val o = JSONObject(json)
        require(o.getInt("schema") == 1) { "Versi proyek belum didukung" }
        val s = o.getJSONObject("settings")
        val settings = ProjectSettings(s.getString("name"), enum(s.getString("aspect"), AspectRatio.Landscape),
            enum(s.getString("resolution"), Resolution.P1080), s.getInt("fps"), s.f("duration"), Color(s.getInt("background")))
        require(settings.fps in 1..240 && settings.duration.isFinite() && settings.duration > 0f)
        val layers = o.getJSONArray("layers")
        val decoded = (0 until layers.length()).map { layer(layers.getJSONObject(it)) }
        require(decoded.map { it.id }.distinct().size == decoded.size)
        return ProjectSnapshot(o.getString("id"), settings, decoded, o.f("time").coerceIn(0f, settings.duration), o.id("selectedId"), o.getLong("savedAt"))
    }

    private fun layer(l: Layer): JSONObject = JSONObject().apply {
        put("id", l.id); put("name", l.name); put("type", l.type.name); put("color", l.color.toArgb())
        put("start", l.start); put("end", l.end); put("easing", l.easing.name); put("text", l.text); put("shape", l.shape.name)
        put("modelId", l.modelId); put("visible", l.visible); put("locked", l.locked); put("solo", l.solo); put("blend", l.blend.name)
        put("parentId", l.parentId); put("sourceIn", l.sourceIn); put("cameraIgnore", l.cameraIgnore); put("targetId", l.targetId); put("followLag", l.followLag)
        put("is3D", l.is3D)
        put("spatialOffset", JSONArray(l.spatialOffset))
        put("parentOffset", arr(listOf(l.parentOffset.a,l.parentOffset.b,l.parentOffset.c,l.parentOffset.d,l.parentOffset.x,l.parentOffset.y)))
        put("tracks", JSONObject().apply { l.tracks.forEach { (p,t) -> put(p.name, track(t)) } })
        put("effects", arr(l.effects.map { e -> JSONObject().put("uid", e.uid).put("type", e.type.name).put("enabled", e.enabled).put("params", arr(e.params.map(::track))) }))
        l.footage?.let { f -> put("footage", JSONObject().put("id", f.id).put("uri", f.uri).put("isImage", f.isImage)
            .put("width", f.width).put("height", f.height).put("duration", f.duration).put("fps", f.fps).put("frameCount", f.frameCount).put("proxyDir", f.proxyDir)) }
        l.tracked?.let { put("tracked", JSONObject().put("x", floats(it.x)).put("y", floats(it.y))) }
        l.stabilize?.let { put("stabilize", JSONObject().put("smoothness", it.smoothness).put("crop", it.crop)
            .put("mRe", floats(it.mRe)).put("mIm", floats(it.mIm)).put("cRe", floats(it.cRe)).put("cIm", floats(it.cIm))) }
        l.cameraSolve?.let { put("cameraSolve", it.toJson()) }
        l.faceSolve?.let { put("faceSolve", it.toJson()) }
    }

    private fun layer(o: JSONObject): Layer {
        val tracks = o.getJSONObject("tracks")
        val effects = o.getJSONArray("effects")
        val offset = o.optJSONArray("parentOffset")
        return Layer(id=o.getInt("id"), name=o.getString("name"), type=LayerType.valueOf(o.getString("type")), color=Color(o.getInt("color")),
            start=o.f("start"), end=o.f("end"), tracks=Prop.entries.associateWith { p -> tracks.optJSONObject(p.name)?.let(::track) ?: Track(p.default) },
            easing=enum(o.optString("easing"), Easing.EaseInOut), text=o.optString("text"), shape=enum(o.optString("shape"), ShapeKind.Circle), modelId=o.optString("modelId", "rocket"),
            visible=o.optBoolean("visible",true), locked=o.optBoolean("locked"), solo=o.optBoolean("solo"), blend=enum(o.optString("blend"),BlendKind.Normal),
            parentId=o.id("parentId"), sourceIn=o.f("sourceIn"), cameraIgnore=o.optBoolean("cameraIgnore"), targetId=o.id("targetId"), followLag=o.f("followLag", .25f),
            parentOffset=if(offset==null) ParentOffset() else ParentOffset(offset.getDouble(0).toFloat(),offset.getDouble(1).toFloat(),offset.getDouble(2).toFloat(),offset.getDouble(3).toFloat(),offset.getDouble(4).toFloat(),offset.getDouble(5).toFloat()),
            effects=(0 until effects.length()).mapNotNull { i ->
                val e=effects.getJSONObject(i); val type=FxType.entries.firstOrNull { it.name==e.getString("type") } ?: return@mapNotNull null
                val params=e.getJSONArray("params")
                EffectInstance(e.getInt("uid"),type,e.optBoolean("enabled",true),type.params.mapIndexed { j,p -> params.optJSONObject(j)?.let(::track) ?: Track(p.default) })
            },
            footage=o.optJSONObject("footage")?.let { f -> Footage(f.getString("id"),f.getString("uri"),f.getBoolean("isImage"),f.getInt("width"),f.getInt("height"),f.f("duration"),f.f("fps"),f.getInt("frameCount"),f.getString("proxyDir")) },
            tracked=o.optJSONObject("tracked")?.let { TrackPath(it.getJSONArray("x").floats(),it.getJSONArray("y").floats()) },
            stabilize=o.optJSONObject("stabilize")?.let { Stabilization(it.f("smoothness"),it.getJSONArray("mRe").floats(),it.getJSONArray("mIm").floats(),it.getJSONArray("cRe").floats(),it.getJSONArray("cIm").floats(),it.f("crop")) },
            is3D=o.optBoolean("is3D"), cameraSolve=o.optJSONObject("cameraSolve")?.let(CameraSolve3D::fromJson),
            faceSolve=o.optJSONObject("faceSolve")?.let(FaceSolve3D::fromJson),
            spatialOffset=o.optJSONArray("spatialOffset")?.let { a -> List(a.length()) { a.getDouble(it).toFloat() }.also { require(it.isEmpty() || it.size==16) } }.orEmpty())
    }
}

