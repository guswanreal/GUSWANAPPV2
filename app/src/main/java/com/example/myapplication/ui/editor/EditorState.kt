package com.example.myapplication.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.example.myapplication.engine.BlendKind
import com.example.myapplication.engine.DemoProjects
import com.example.myapplication.engine.Easing
import com.example.myapplication.engine.EffectInstance
import com.example.myapplication.engine.Footage
import com.example.myapplication.engine.KEY_EPS
import com.example.myapplication.engine.ProjectSettings
import com.example.myapplication.engine.FxType
import com.example.myapplication.engine.Keyframe
import com.example.myapplication.engine.Layer
import com.example.myapplication.engine.LayerType
import com.example.myapplication.engine.Prop
import com.example.myapplication.engine.PropKey
import com.example.myapplication.engine.ShapeKind
import com.example.myapplication.engine.Template
import com.example.myapplication.engine.Track
import com.example.myapplication.engine.screenDeltaToParentSpace
import com.example.myapplication.engine.canParentLayer
import com.example.myapplication.engine.reparentLayer
import com.example.myapplication.engine.ProjectSnapshot
import com.example.myapplication.engine.CameraSolve3D
import com.example.myapplication.engine.CurvePreset
import com.example.myapplication.engine.Handle
import com.example.myapplication.engine.segmentHandles
import com.example.myapplication.engine.FaceSolve3D
import com.example.myapplication.engine.FacePose3D
import com.example.myapplication.engine.EffectPreset
import com.example.myapplication.engine.footageToScreen
import com.example.myapplication.ui.theme.LayerColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Panels opened from the editor's bottom toolbar, Alight Motion style. */
enum class EditorPanel(val label: String) {
    Transform("Transform"), Effects("Effect Controls"), Graph("Graph Editor"), Color("Warna"),
    Blending("Blending & Opacity"), Parent("Parent & Link"), Text("Teks"), Shape("Bentuk"), Model("Model 3D"), Tracker("Tracking"),
}

class EditorState(val settings: ProjectSettings, template: Template?, restored: ProjectSnapshot? = null,
    private val onSave: (ProjectSnapshot) -> Unit = {}) {
    val projectId = restored?.id ?: java.util.UUID.randomUUID().toString()
    val projectName = settings.name
    val duration = settings.duration
    val fps = settings.fps
    val frame = 1f / fps

    var layers by mutableStateOf(restored?.layers ?: template?.layers()?.map(::fitToDuration).orEmpty())
        private set
    var selectedId by mutableStateOf(restored?.selectedId ?: if (template != null) 5 else null)
    var time by mutableFloatStateOf(restored?.time ?: 0f)
    var playing by mutableStateOf(false)
    var loop by mutableStateOf(true)
    var showGuides by mutableStateOf(false)

    var panel by mutableStateOf<EditorPanel?>(null)
    var parentError by mutableStateOf<String?>(null)
        private set
    /** Timeline zoom, in dp per second of comp time. */
    var dpPerSecond by mutableFloatStateOf(72f)
    var graphKey by mutableStateOf<PropKey?>(null)
    var graphKeyIndex by mutableStateOf<Int?>(null)
    var expandedFx by mutableStateOf<Set<Int>>(emptySet())

    private val undoStack = ArrayDeque<List<Layer>>()
    private val redoStack = ArrayDeque<List<Layer>>()
    private var lastCoalesce: String? = null
    private var lastCommitAt = 0L
    private var historyVersion by mutableIntStateOf(0)

    val canUndo: Boolean get() { historyVersion; return undoStack.isNotEmpty() }
    val canRedo: Boolean get() { historyVersion; return redoStack.isNotEmpty() }

    val selected: Layer? get() = layers.firstOrNull { it.id == selectedId }
    fun snapshot() = ProjectSnapshot(projectId, settings, layers, time, selectedId)
    fun save() = onSave(snapshot())

    fun applyCameraSolve(videoId:Int, solve:CameraSolve3D) {
        val video=layers.firstOrNull { it.id==videoId } ?: return
        val id=(layers.maxOfOrNull { it.id } ?: 0)+1
        val camera=Layer(id,"Camera 3D · ${video.name}",LayerType.Camera,video.color,solve.poses.first().time,solve.solvedUntil,cameraSolve=solve)
        val point=solve.points.minByOrNull { it.x*it.x+it.y*it.y } ?: return
        val fitHeight=minOf(settings.height.toFloat(),settings.width/solve.sourceAspect)
        val scale=solve.focalRatio/solve.referenceDepth*fitHeight/settings.height
        val nullLayer=Layer(id+1,"Tracked Null",LayerType.Null,video.color,camera.start,camera.end,is3D=true)
            .withTrack(Prop.PosX,Track(point.x*scale*2*settings.height/settings.width))
            .withTrack(Prop.PosY,Track(point.y*scale*2))
            .withTrack(Prop.PosZ,Track((point.z-solve.referenceDepth)*scale))
        commit(layers.map { if(it.id==videoId) it.copy(cameraIgnore=true) else it }+camera+nullLayer)
    }

    fun applyFaceSolve(videoId: Int, solve: FaceSolve3D) {
        val video=layers.firstOrNull { it.id==videoId } ?: return
        require(solve.poses.size>=3)
        val id=(layers.maxOfOrNull { it.id } ?: 0)+1
        val reference=solve.poses.first().width.coerceAtLeast(.01f)
        val source=video.copy(cameraIgnore=true,faceSolve=solve)
        val scene=layers.map { if(it.id==videoId) source else it }
        val size=Size(settings.width.toFloat(),settings.height.toFloat())
        val positions=solve.poses.map { p -> footageToScreen(scene,videoId,p.time,size,p.x,p.y) ?: Offset(size.width/2,size.height/2) }
        fun track(value: (FacePose3D,Int)->Float)=Track(0f,solve.poses.mapIndexed { i,p -> Keyframe(p.time,value(p,i),Easing.Linear) })
        fun distance(p:FacePose3D)=(reference/p.width.coerceAtLeast(.01f)).coerceIn(.25f,4f)
        val rig=Layer(id,"Face Null 3D",LayerType.Null,video.color,solve.poses.first().time,solve.poses.last().time,
            is3D=true,cameraIgnore=true,easing=Easing.Linear,faceSolve=solve)
            .withTrack(Prop.PosX,track { p,i -> (positions[i].x/size.width*2-1)*distance(p) })
            .withTrack(Prop.PosY,track { p,i -> (positions[i].y/size.height*2-1)*distance(p) })
            .withTrack(Prop.PosZ,track { p,_ -> (.8660254f*(distance(p)-1)).coerceAtLeast(-.7f) })
            .withTrack(Prop.OrbitX,track { p,_ -> p.pitch })
            .withTrack(Prop.OrbitY,track { p,_ -> p.yaw })
            .withTrack(Prop.Rotation,track { p,_ -> p.roll })
        commit(scene+rig)
        select(id); panel=EditorPanel.Transform
    }

    /** Templates are authored for 10 s; clips that run to their end are stretched to fill the project. */
    private fun fitToDuration(l: Layer): Layer = when {
        l.end >= DemoProjects.DURATION - 1e-3f || l.end > duration -> l.copy(end = duration, start = l.start.coerceAtMost(duration - 5 * frame))
        else -> l
    }

    /** Applies an edit. Edits sharing a [coalesce] key in quick succession (drags) form one undo step. */
    fun commit(newLayers: List<Layer>, coalesce: String? = null) {
        if (newLayers == layers) return
        val now = System.currentTimeMillis()
        val merge = coalesce != null && coalesce == lastCoalesce && now - lastCommitAt < 800
        if (!merge) {
            undoStack.addLast(layers)
            if (undoStack.size > 120) undoStack.removeFirst()
        }
        redoStack.clear()
        lastCoalesce = coalesce
        lastCommitAt = now
        layers = newLayers
        historyVersion++
        save()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(layers)
        layers = prev
        lastCoalesce = null
        historyVersion++
        save()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(layers)
        layers = next
        lastCoalesce = null
        historyVersion++
        save()
    }

    fun select(id: Int?) {
        selectedId = id
        parentError = null
        graphKeyIndex = null
        if (id == null) panel = null
        save()
    }

    fun updateSelected(coalesce: String? = null, f: (Layer) -> Layer) {
        val id = selectedId ?: return
        updateLayer(id, coalesce, f)
    }

    fun updateLayer(id: Int, coalesce: String? = null, f: (Layer) -> Layer) {
        commit(layers.map { if (it.id == id) f(it) else it }, coalesce)
    }

    // ---------------------------------------------------------------- Keyframable values

    fun valueOf(layer: Layer, key: PropKey): Float {
        val track = layer.trackOf(key) ?: return 0f
        return track.valueAt(time, layer.easing)
    }

    /** Sets a value at the playhead; writes a keyframe when the property's stopwatch is on. */
    fun setValue(key: PropKey, value: Float) = updateSelected("value-$key-$selectedId") { l ->
        if(!value.isFinite()) return@updateSelected l
        val tr = l.trackOf(key) ?: return@updateSelected l
        l.withTrackOf(key, tr.withValueAt(snap(time), value))
    }

    /** After Effects' stopwatch: on records the first keyframe, off clears them and keeps the current value. */
    fun toggleStopwatch(key: PropKey) = updateSelected { l ->
        val tr = l.trackOf(key) ?: return@updateSelected l
        val current = tr.valueAt(time, l.easing)
        l.withTrackOf(key, if (tr.animated) Track(current) else Track(current, listOf(Keyframe(snap(time), current))))
    }

    /** The keyframe navigator's diamond: adds a key at the playhead, or removes the one sitting there. */
    fun toggleKeyframeHere(key: PropKey) = updateSelected { l ->
        val tr = l.trackOf(key) ?: return@updateSelected l
        val t = snap(time)
        val next = if (tr.hasKeyAt(t)) {
            val remaining = tr.keys.filterNot { abs(it.time - t) < KEY_EPS }
            if (remaining.isEmpty()) Track(tr.valueAt(time, l.easing)) else tr.copy(keys = remaining)
        } else {
            val v = tr.valueAt(time, l.easing)
            if (tr.animated) tr.withValueAt(t, v) else Track(v, listOf(Keyframe(t, v)))
        }
        l.withTrackOf(key, next)
    }

    /** Jumps the playhead to the previous/next key of [key], or of the whole selected layer when null. */
    fun jumpKey(key: PropKey?, next: Boolean) {
        val l = selected
        val times = when {
            l == null -> layers.flatMap { it.keyTimes }
            key == null -> l.keyTimes
            else -> l.trackOf(key)?.keys?.map { it.time }.orEmpty()
        } + if (key == null) listOf(0f, duration) else emptyList()
        val target = if (next) times.filter { it > time + frame / 2 }.minOrNull() else times.filter { it < time - frame / 2 }.maxOrNull()
        if (target != null) { playing = false; time = target }
    }

    // ---------------------------------------------------------------- Graph editor

    /** Moves keyframe [index] of [key]; it stays between its neighbours so the order never flips. */
    fun moveKey(key: PropKey, index: Int, newTime: Float, newValue: Float) = updateSelected("graph-$key-$index") { l ->
        val tr = l.trackOf(key) ?: return@updateSelected l
        val k = tr.keys.getOrNull(index) ?: return@updateSelected l
        if (!newTime.isFinite() || !newValue.isFinite()) return@updateSelected l
        val lo = tr.keys.getOrNull(index - 1)?.time?.plus(KEY_EPS * 1.01f) ?: 0f
        val hi = tr.keys.getOrNull(index + 1)?.time?.minus(KEY_EPS * 1.01f) ?: duration
        val t = snap(newTime).coerceIn(lo, hi.coerceAtLeast(lo))
        l.withTrackOf(key, tr.copy(keys = tr.keys.toMutableList().apply { set(index, k.copy(time = t, value = newValue)) }))
    }

    /** Sets the outgoing interpolation of one key, or of every key when [index] is null. */
    fun setKeyEasing(key: PropKey, index: Int?, easing: Easing) = updateSelected { l ->
        val tr = l.trackOf(key) ?: return@updateSelected l
        l.withTrackOf(key, tr.copy(keys = tr.keys.mapIndexed { i, k -> if (index == null || i == index) k.copy(easing = easing) else k }))
    }

    fun deleteKey(key: PropKey, index: Int) = updateSelected { l ->
        val tr = l.trackOf(key) ?: return@updateSelected l
        val remaining = tr.keys.filterIndexed { i, _ -> i != index }
        graphKeyIndex = null
        l.withTrackOf(key, if (remaining.isEmpty()) Track(tr.valueAt(time, l.easing)) else tr.copy(keys = remaining))
    }

    fun setLoop(key: PropKey, loop: Boolean) = updateSelected { l ->
        val tr = l.trackOf(key) ?: return@updateSelected l
        l.withTrackOf(key, tr.copy(loop = loop))
    }

    fun applyCurve(key: PropKey, index: Int?, curve: CurvePreset) = updateSelected {
        val tr = it.trackOf(key) ?: return@updateSelected it
        it.withTrackOf(key, curve.apply(tr, index?.coerceAtMost(tr.keys.lastIndex-1)))
    }

    fun setBezierHandle(key: PropKey, segment: Int, outgoing: Boolean, influence: Float, speed: Float) =
        updateSelected("handle-$key-$segment-$outgoing") { l ->
            if (!influence.isFinite() || !speed.isFinite()) return@updateSelected l
            val tr=l.trackOf(key) ?: return@updateSelected l
            val a=tr.keys.getOrNull(segment) ?: return@updateSelected l
            val b=tr.keys.getOrNull(segment+1) ?: return@updateSelected l
            val handles=segmentHandles(a,b,a.easing ?: l.easing) ?: (Handle(1f/3,0f) to Handle(1f/3,0f))
            val out=if(outgoing) Handle(influence.coerceIn(.001f,1f),speed) else handles.first
            val incoming=if(!outgoing) Handle(influence.coerceIn(.001f,1f),speed) else handles.second
            l.withTrackOf(key,tr.copy(keys=tr.keys.toMutableList().apply {
                set(segment,a.copy(easing=Easing.Bezier,outInfluence=out.influence,outSpeed=out.speed))
                set(segment+1,b.copy(inInfluence=incoming.influence,inSpeed=incoming.speed))
            }))
        }

    fun createCameraRig() {
        val camera=selected?.takeIf { it.type==LayerType.Camera } ?: return
        val id=(layers.maxOfOrNull { it.id } ?: 0)+1
        val rig=Layer(id,"Camera Rig",LayerType.Null,camera.color,0f,duration,is3D=true)
        val next=layers+rig
        val linked=reparentLayer(next,camera.id,rig.id,time,Size(settings.width.toFloat(),settings.height.toFloat()))
        commit(next.map { if(it.id==camera.id) linked else it })
        select(id)
        panel=EditorPanel.Transform
    }

    // ---------------------------------------------------------------- Direct manipulation in the preview

    /** One-finger drag moves, pinch scales, two-finger twist rotates the selected layer. */
    fun transformSelected(pan: Offset, zoom: Float, rotation: Float, size: Size) {
        val l = selected ?: return
        if (l.locked || l.type == LayerType.Gradient) return
        val t = snap(time)
        time = t
        val d = screenDeltaToParentSpace(layers, l.id, t, size, pan)
        var next = l
        fun put(p: Prop, v: Float) {
            if (v.isFinite()) next = next.withTrack(p, next.track(p).withValueAt(t, if(p==Prop.Scale) v.coerceIn(.001f,p.max) else v))
        }
        if (d != Offset.Zero) {
            put(Prop.PosX, l.value(Prop.PosX, t) + d.x / (size.width / 2f))
            put(Prop.PosY, l.value(Prop.PosY, t) + d.y / (size.height / 2f))
        }
        if (zoom != 1f) put(Prop.Scale, l.value(Prop.Scale, time) * zoom)
        if (rotation != 0f) put(Prop.Rotation, l.value(Prop.Rotation, time) + rotation)
        commit(layers.map { if (it.id == l.id) next else it }, "gesture-${l.id}")
    }

    // ---------------------------------------------------------------- Effects

    private fun nextEffectUid() = (layers.flatMap { it.effects }.maxOfOrNull { it.uid } ?: 1000) + 1

    fun addEffect(type: FxType) {
        if (selected?.let { type.appliesTo(it.type) } != true) return
        val uid = nextEffectUid()
        updateSelected { it.copy(effects = it.effects + EffectInstance.of(type, uid)) }
        expandedFx = expandedFx + uid
        panel = EditorPanel.Effects
    }

    fun addPreset(preset: EffectPreset) {
        val layer=selected ?: return
        if(!preset.appliesTo(layer.type)) return
        val added=preset.build(layer,time,nextEffectUid())
        updateSelected { it.copy(effects=it.effects+added) }
        expandedFx=expandedFx+added.map { it.uid }
        panel=EditorPanel.Effects
    }

    fun removeEffect(uid: Int) = updateSelected { l -> l.copy(effects = l.effects.filterNot { it.uid == uid }) }

    fun toggleEffect(uid: Int) = updateSelected { l -> l.copy(effects = l.effects.map { if (it.uid == uid) it.copy(enabled = !it.enabled) else it }) }

    fun resetEffect(uid: Int) = updateSelected { l -> l.copy(effects = l.effects.map { if (it.uid == uid) EffectInstance.of(it.type, uid) else it }) }

    fun moveEffect(uid: Int, up: Boolean) = updateSelected { l ->
        val i = l.effects.indexOfFirst { it.uid == uid }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in l.effects.indices) l else l.copy(effects = l.effects.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun toggleExpanded(uid: Int) { expandedFx = if (uid in expandedFx) expandedFx - uid else expandedFx + uid }

    // ---------------------------------------------------------------- Layer switches

    /** Layers the selection may be parented to: anything that isn't itself or one of its descendants. */
    fun parentCandidates(): List<Layer> {
        val me = selected ?: return emptyList()
        return layers.filter { canParentLayer(layers, me.id, it.id) }.asReversed()
    }

    fun setParent(parentId: Int?) {
        val layer = selected ?: return
        if (layer.locked || layer.parentId == parentId) return
        parentError = null
        try {
            val next = reparentLayer(layers, layer.id, parentId, time,
                Size(settings.width.toFloat(), settings.height.toFloat()))
            val faceParent=layers.firstOrNull { it.id==parentId }?.let { it.type==LayerType.Null && it.faceSolve!=null } == true
            updateSelected { if(faceParent) next.copy(cameraIgnore=true) else next }
        } catch (e: IllegalArgumentException) {
            parentError = e.message
        }
    }

    fun centerOnParent() = updateSelected { layer ->
        if(layer.parentId==null || layer.locked) return@updateSelected layer
        var next=layer.copy(parentOffset=com.example.myapplication.engine.ParentOffset(),spatialOffset=emptyList())
        for(p in listOf(Prop.PosX,Prop.PosY,Prop.PosZ)) next=next.withTrack(p,next.track(p).withValueAt(snap(time),0f))
        next
    }

    fun setBlend(blend: BlendKind) = updateSelected { it.copy(blend = blend) }

    fun setColor(color: Color, coalesce: String? = null) = updateSelected(coalesce) { it.copy(color = color) }

    fun toggleVisible(id: Int) = updateLayer(id) { it.copy(visible = !it.visible) }
    fun toggleLock(id: Int) = updateLayer(id) { it.copy(locked = !it.locked) }
    fun toggleSolo(id: Int) = updateLayer(id) { it.copy(solo = !it.solo) }

    // ---------------------------------------------------------------- Layers

    fun addLayer(type: LayerType, modelId: String = "robot", footage: Footage? = null) {
        if ((type == LayerType.Video || type == LayerType.Image) && footage == null) return
        val id = (layers.maxOfOrNull { it.id } ?: 0) + 1
        val color = LayerColors[id % LayerColors.size]
        val start = snap(time).coerceAtMost(duration - 1f)
        val layer = when (type) {
            LayerType.Text -> Layer(id, "Teks $id", type, color, start, duration, text = "Teks Baru")
                .withTrack(Prop.Scale, Track(0.6f))
            LayerType.Shape -> Layer(id, "Shape $id", type, color, start, duration, shape = ShapeKind.entries[id % ShapeKind.entries.size])
                .withTrack(Prop.Scale, Track(0.5f))
            LayerType.Model3D -> Layer(id, "Model 3D $id", type, color, start, duration, modelId = modelId)
                .withTrack(Prop.Scale, Track(0.7f))
                .withTrack(Prop.Rotation, Track(0f, listOf(Keyframe(start, 0f), Keyframe(duration, 360f))))
            LayerType.Particles -> Layer(id, "Partikel $id", type, color, start, duration)
            LayerType.Gradient -> Layer(id, "Gradien $id", type, color, start, duration)
            LayerType.Camera -> Layer(id, "Kamera $id", type, color, 0f, duration)
            LayerType.Null -> Layer(id, "Null $id", type, color, start, duration)
            LayerType.Video -> Layer(id, "Video $id", type, color, start, minOf(duration, start + footage!!.duration), footage = footage)
            LayerType.Image -> Layer(id, "Foto $id", type, color, start, duration, footage = footage)
        }
        // Gradients and footage are backgrounds, so they go to the bottom of the stack; the camera sits on top.
        val toBottom = type == LayerType.Gradient || type == LayerType.Video || type == LayerType.Image
        commit(if (toBottom) listOf(layer) + layers else layers + layer)
        select(id)
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        // Preserve children's current visual transform before removing their parent's data.
        val size = Size(settings.width.toFloat(), settings.height.toFloat())
        commit(layers.filterNot { it.id == id }.map { layer ->
            if (layer.parentId == id) reparentLayer(layers, layer.id, null, time, size) else layer
        })
        select(null)
    }

    fun duplicateSelected() {
        val s = selected ?: return
        val id = (layers.maxOfOrNull { it.id } ?: 0) + 1
        var uid = nextEffectUid()
        val copy = s.copy(id = id, name = "${s.name} salinan", effects = s.effects.map { it.copy(uid = uid++) })
        val i = layers.indexOf(s)
        commit(layers.toMutableList().apply { add(i + 1, copy) })
        select(id)
    }

    /** Moves the selected layer up (towards the front) or down in the stack. */
    fun moveSelected(up: Boolean) {
        val i = layers.indexOfFirst { it.id == selectedId }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in layers.indices) return
        commit(layers.toMutableList().apply { add(j, removeAt(i)) })
    }

    /** Slides a clip and all of its keyframes in time. */
    fun shiftLayer(id: Int, delta: Float) = updateLayer(id, "shift-$id") { l ->
        val d = delta.coerceIn(-l.start, duration - l.end)
        l.copy(
            start = l.start + d, end = l.end + d,
            tracks = l.tracks.mapValues { (_, tr) -> tr.shifted(d) },
            effects = l.effects.map { e -> e.copy(params = e.params.map { it.shifted(d) }) },
        )
    }

    /** Trims a clip's in or out point, keeping at least a few frames. */
    fun trim(id: Int, startDelta: Float, endDelta: Float) = updateLayer(id, "trim-$id") { l ->
        val minLen = minOf(5 * frame,l.end-l.start).coerceAtLeast(KEY_EPS)
        val video=l.footage?.takeUnless { it.isImage }
        val minStart=if(video==null) 0f else (l.start-l.sourceIn).coerceAtLeast(0f)
        val maxEnd=if(video==null) duration else minOf(duration,l.start+video.duration-l.sourceIn)
        val start=(l.start+startDelta).coerceIn(minStart,(l.end-minLen).coerceAtLeast(minStart))
        l.copy(
            start = start,
            end = (l.end + endDelta).coerceIn(start+minLen,maxEnd.coerceAtLeast(start+minLen)),
            sourceIn = if(video!=null) (l.sourceIn+start-l.start).coerceAtLeast(0f) else l.sourceIn,
        )
    }

    fun seek(t: Float) { time = snap(t.coerceIn(0f, duration)); save() }

    fun snap(t: Float) = (t * fps).roundToInt() / fps.toFloat()

    fun timecode(t: Float) = formatTimecode(t, fps)
}

/** SMPTE-style mm:ss:ff at the given frame rate. */
fun formatTimecode(t: Float, fps: Int): String {
    val totalFrames = (t * fps).roundToInt()
    val s = totalFrames / fps
    val f = totalFrames % fps
    val digits = if (fps > 99) "%03d" else "%02d"
    return "%02d:%02d:".format(s / 60, s % 60) + digits.format(f)
}

