package com.example.myapplication.engine

import android.graphics.RenderNode
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val CompBackground = DefaultBackground
private val SelectionColor = Color(0xFFFF6A2B)

/** Everything one frame of the composition needs besides the time. */
private class Scene(val layers: List<Layer>, val size: Size, val frames: FrameSource, val fps: Int) {
    val byId: Map<Int, Layer> = layers.associateBy { it.id }
    private val transforms = HashMap<Float, LayerHierarchy>()
    fun hierarchy(t: Float) = transforms.getOrPut(t) { LayerHierarchy(byId, t, size) }
    fun parent(layer: Layer, t: Float) = hierarchy(t).parentCompose(layer)
    fun world(layer: Layer, t: Float) = hierarchy(t).worldCompose(layer)
    private val spatial = HashMap<Float, Scene3D>()
    fun space(t: Float) = spatial.getOrPut(t) { Scene3D(layers,t,size) }
}

/** The comp camera at one moment: where it looks, zoom, roll and 3D orbit. */
private class CamState(val poi: Offset, val zoom: Float, val roll: Float, val orbitX: Float, val orbitY: Float)

/**
 * Text layouts a composition's TextMeasurer should keep. Animated text lays out every glyph on
 * its own, so the default cache of 8 misses on every frame for any title longer than a few letters.
 */
const val COMPOSITION_TEXT_CACHE = 128

/** Renders every active layer of a composition at time [t] into the current DrawScope. */
fun DrawScope.drawComposition(
    layers: List<Layer>,
    t: Float,
    textMeasurer: TextMeasurer,
    selectedId: Int? = null,
    background: Color = CompBackground,
    frames: FrameSource = FootageFrames,
    fps: Int = FPS,
) {
    drawRect(background)
    // Reading the frame cache's version makes the preview redraw when a proxy frame arrives.
    if (frames === FootageFrames && layers.any { it.footage != null }) FootageFrames.version
    val scene = Scene(layers, size, frames, fps)
    val anySolo = layers.any { it.solo && it.type != LayerType.Null && it.type != LayerType.Camera }
    // RenderEffect only works on hardware canvases; a software canvas falls back to plain layers.
    val gpu = drawContext.canvas.nativeCanvas.isHardwareAccelerated
    for (layer in scene.space(t).drawOrder()) {
        if (layer.type == LayerType.Camera || layer.type == LayerType.Null) continue
        if (!layer.activeAt(t) || (anySolo && !layer.solo)) continue
        val echo = layer.fx(FxType.Echo)
        if (echo != null && layer.type != LayerType.Gradient) {
            val count = echo.param(0, t, layer.easing).toInt().coerceIn(1, 8)
            val gap = echo.param(1, t, layer.easing)
            // 3D ghosts are expensive, so models get at most two.
            val n = if (layer.type == LayerType.Model3D) min(count, 2) else count
            for (i in n downTo 1) {
                val past = t - i * gap
                if (past >= layer.start) drawLayerPlain(scene, layer, past, textMeasurer, alphaMul = 0.55f * (n + 1 - i) / (n + 1))
            }
        }
        drawLayerComposited(scene, layer, t, textMeasurer, gpu)
        if (layer.id == selectedId && layer.type != LayerType.Gradient) drawSelection(scene, layer, t, textMeasurer)
    }
    // Null controls are editor overlays only: export and thumbnails pass no selectedId.
    scene.byId[selectedId]?.takeIf { it.type == LayerType.Null && it.activeAt(t) }?.let {
        drawSelection(scene, it, t, textMeasurer)
    }
}

// ---------------------------------------------------------------- Transforms

private fun shakeNoise(x: Float, seed: Float) =
    sin(x * 6.2831f + seed) * 0.55f + sin(x * 6.2831f * 2.37f + seed * 1.7f) * 0.3f + sin(x * 6.2831f * 4.13f + seed * 2.3f) * 0.15f

/** Position in comp pixels, including the wiggle() expression and Shake. */
fun layerPosition(layer: Layer, t: Float, size: Size): Offset {
    var x = layer.value(Prop.PosX, t)
    var y = layer.value(Prop.PosY, t)
    layer.fx(FxType.Wiggle)?.let { w ->
        val f = w.param(0, t, layer.easing)
        val amp = w.param(1, t, layer.easing)
        x += (sin(t * f * 3.3f) * 0.6f + sin(t * f * 1.9f + 1.3f) * 0.4f) * amp
        y += (cos(t * f * 2.9f) * 0.6f + sin(t * f * 1.5f + 2.1f) * 0.4f) * amp
    }
    var p = Offset(size.width / 2f * (1 + x), size.height / 2f * (1 + y))
    layer.fx(FxType.Shake)?.let { s ->
        val amp = s.param(0, t, layer.easing) * size.minDimension / 1080f
        val f = s.param(1, t, layer.easing)
        p += Offset(shakeNoise(t * f, 1.3f), shakeNoise(t * f, 4.7f)) * amp
    }
    return p
}

fun anchorOffset(layer: Layer, t: Float, size: Size) =
    Offset(layer.value(Prop.AnchorX, t) * size.width / 2f, layer.value(Prop.AnchorY, t) * size.height / 2f)

/** Model layers spin around Y in 3D, so their Rotation never tilts the 2D frame. */
internal fun planarRotation(layer: Layer, t: Float): Float {
    if (layer.type == LayerType.Model3D) return 0f
    val shake = layer.fx(FxType.Shake)?.let { s -> s.param(2, t, layer.easing) * shakeNoise(t * s.param(1, t, layer.easing), 7.1f) } ?: 0f
    return layer.value(Prop.Rotation, t) + shake
}

internal fun layerScale(layer: Layer, t: Float): Float {
    val shake = layer.fx(FxType.Shake)?.let { s -> 1f + s.param(3, t, layer.easing) * abs(shakeNoise(t * s.param(1, t, layer.easing), 2.9f)) } ?: 1f
    return layer.value(Prop.Scale, t) * shake
}

/**
 * Parent & Link: a child lives in its parent's layer space, where the comp centre maps onto
 * the parent's anchor. A parent at the comp centre with no rotation or scale changes nothing.
 */
fun parentMatrix(layer: Layer, byId: Map<Int, Layer>, t: Float, size: Size): Matrix {
    return LayerHierarchy(byId, t, size).parentCompose(layer)
}

/** Parent transform followed by the layer's own Position · Rotation · Scale · −Anchor Point. */
fun layerMatrix(layer: Layer, byId: Map<Int, Layer>, t: Float, size: Size): Matrix {
    return LayerHierarchy(byId, t, size).worldCompose(layer)
}

/** [first] applied, then [then]: a new matrix. */
private fun concat(first: Matrix, then: Matrix?): Matrix {
    if (then == null) return first
    return Matrix(first.values.copyOf()).apply { timesAssign(then) }
}

/** Compose leaves a singular matrix unchanged on invert; never use that as an inverse. */
private fun inverseOrNull(matrix: Matrix): Matrix? {
    val v = matrix.values
    val determinant = v[0]*(v[5]*v[15]-v[13]*v[7])-v[4]*(v[1]*v[15]-v[13]*v[3])+v[12]*(v[1]*v[7]-v[5]*v[3])
    if (!determinant.isFinite() || abs(determinant) < 1e-8f) return null
    return Matrix(v.copyOf()).apply { invert() }
}

/** World position of a layer's anchor in comp pixels (before the camera). */
private fun Scene.worldPosition(layer: Layer, t: Float): Offset = parent(layer, t).map(layerPosition(layer, t, size))

private fun Scene.cameraAt(t: Float): CamState? {
    val cam = layers.lastOrNull { it.type == LayerType.Camera && it.activeAt(t) } ?: return null
    val offset = Offset(cam.value(Prop.PosX, t) * size.width / 2f, cam.value(Prop.PosY, t) * size.height / 2f)
    val target = cam.targetId?.let { byId[it] }?.takeIf { it.id != cam.id && it.type != LayerType.Camera }
    val poi = if (target != null) {
        // Auto-follow: average the target's path over the lag window, so the camera trails smoothly.
        val lag = cam.followLag.coerceIn(0f, 2f)
        var sx = 0f; var sy = 0f
        for (s in 0 until 8) {
            val p = worldPosition(target, t - lag * s / 7f)
            sx += p.x; sy += p.y
        }
        Offset(sx / 8f, sy / 8f) + offset
    } else {
        Offset(size.width / 2f, size.height / 2f) + offset
    }
    val zoom=cam.value(Prop.Scale,t).coerceAtLeast(.05f)
    if(cam.parentId==null && cam.spatialOffset.isEmpty())
        return CamState(poi,zoom,cam.value(Prop.Rotation,t),cam.value(Prop.OrbitX,t),cam.value(Prop.OrbitY,t))
    val rig=space(t).cameraRigMatrix()
    val scale=kotlin.math.sqrt(rig[0]*rig[0]+rig[1]*rig[1]+rig[2]*rig[2]).coerceAtLeast(.001f)
    val x=(poi.x-size.width/2)/size.height; val y=(poi.y-size.height/2)/size.height
    val point=Offset(size.width/2+(rig[0]*x+rig[4]*y+rig[12])*size.height,
        size.height/2+(rig[1]*x+rig[5]*y+rig[13])*size.height)
    val degrees=180f/PI.toFloat()
    val roll=atan2(rig[1],rig[0])*degrees
    val yaw=kotlin.math.asin((-rig[2]/scale).coerceIn(-1f,1f))*degrees
    val pitch=atan2(rig[6],rig[10])*degrees
    val rigZoom=zoom/(scale-rig[14]*zoom/.8660254f).coerceAtLeast(.05f)
    return CamState(point,rigZoom,cam.value(Prop.Rotation,t)+roll,cam.value(Prop.OrbitX,t)+pitch,cam.value(Prop.OrbitY,t)+yaw)
}

/** Comp → screen for a layer at its Position Z: farther layers shrink and move less (parallax). */
private fun Scene.cameraMatrix(cam: CamState?, layer: Layer, t: Float): Matrix? {
    if (cam == null || layer.cameraIgnore || layer.type == LayerType.Gradient) return null
    val depth = if (Prop.PosZ in layer.type.transformProps()) layer.value(Prop.PosZ, t) else 0f
    val k = cam.zoom / (1f + depth).coerceAtLeast(0.15f)
    val shiftX = -sin(cam.orbitY * (PI / 180.0).toFloat()) * depth * size.width * 0.5f * k
    val shiftY = sin(cam.orbitX * (PI / 180.0).toFloat()) * depth * size.height * 0.5f * k
    val m = Matrix()
    m.translate(size.width / 2f + shiftX, size.height / 2f + shiftY)
    m.rotateZ(-cam.roll)
    m.scale(k, k)
    m.translate(-cam.poi.x, -cam.poi.y)
    return m
}

private fun Scene.fullMatrix(layer: Layer, t: Float): Matrix =
    if (layer.is3D) space(t).matrix(layer) ?: Matrix().apply { scale(0f,0f) }
    else concat(world(layer, t), cameraMatrix(cameraAt(t), layer, t))

/** Converts a screen-space drag into the layer's parent space, so children and camera views move with the finger. */
fun screenDeltaToParentSpace(layers: List<Layer>, layerId: Int, t: Float, size: Size, delta: Offset): Offset {
    val scene = Scene(layers, size, FootageFrames, FPS)
    val layer = scene.byId[layerId] ?: return delta
    if (layer.is3D) {
        val anchor=anchorOffset(layer,t,size)
        val origin=scene.space(t).screenPoint(layer,anchor) ?: return Offset.Zero
        fun step(p: Prop): Offset {
            val next=layer.withTrack(p,Track(layer.value(p,t)+.001f))
            return (Scene3D(layers.map { if(it.id==layer.id) next else it },t,size).screenPoint(next,anchor) ?: origin)-origin
        }
        val x=step(Prop.PosX); val y=step(Prop.PosY); val det=x.x*y.y-x.y*y.x
        if (abs(det)<1e-8f) return Offset.Zero
        return Offset((delta.x*y.y-delta.y*y.x)/det*.001f*size.width/2,
            (x.x*delta.y-x.y*delta.x)/det*.001f*size.height/2)
    }
    val basis = concat(scene.parent(layer, t), scene.cameraMatrix(scene.cameraAt(t), layer, t))
    val inv = inverseOrNull(basis) ?: return Offset.Zero
    return inv.map(delta) - inv.map(Offset.Zero)
}

/** Footage fitted inside the comp, keeping its aspect ratio. */
fun footageFit(f: Footage, size: Size): Size =
    if (f.aspect > size.width / size.height) Size(size.width, size.width / f.aspect) else Size(size.height * f.aspect, size.height)

// ---------------------------------------------------------------- Layer drawing

/** Sub-frame times for Motion Blur, or just [t] when the layer isn't moving. */
private fun Scene.motionSamples(layer: Layer, t: Float): List<Float> {
    val mb = layer.fx(FxType.MotionBlur) ?: return listOf(t)
    val shutter = mb.param(0, t, layer.easing) / 360f / fps
    if (shutter < 1e-4f) return listOf(t)
    var n = mb.param(1, t, layer.easing).toInt().coerceIn(2, 16)
    if (layer.type == LayerType.Model3D) n = min(n, 6)
    val t0 = t - shutter / 2f; val t1 = t + shutter / 2f
    if (!isMoving(layer, t0, t1)) return listOf(t)
    return List(n) { i -> t0 + shutter * (i + 0.5f) / n }
}

private fun Scene.isMoving(layer: Layer, t0: Float, t1: Float): Boolean {
    if (layer.footage?.isImage == false || layer.type == LayerType.Particles) return true
    if (layer.effects.any { e -> e.enabled && e.params.any { it.animated } }) return true
    val a = fullMatrix(layer, t0).values; val b = fullMatrix(layer, t1).values
    for (i in 0 until 16) {
        val tol = if (i == 12 || i == 13) 0.4f else 0.002f
        if (abs(a[i] - b[i]) > tol) return true
    }
    return layer.type == LayerType.Model3D && abs(layer.value(Prop.Rotation, t0) - layer.value(Prop.Rotation, t1)) > 0.1f
}

private fun DrawScope.drawLayerComposited(scene: Scene, layer: Layer, t: Float, tm: TextMeasurer, gpu: Boolean) {
    val opacity = layer.value(Prop.Opacity, t).coerceIn(0f, 1f)
    if (opacity <= 0.001f) return
    val samples = scene.motionSamples(layer, t)
    val needsGpu = gpu && (samples.size > 1 || layer.effects.any { it.enabled && it.type !in GeometricFx })
    if (!needsGpu) {
        val blended = layer.blend != BlendKind.Normal
        if (blended) drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { blendMode = layer.blend.mode }) }
        for (s in samples) drawLayerPlain(scene, layer, s, tm, alphaMul = 1f / samples.size)
        if (blended) drawIntoCanvas { it.restore() }
        return
    }

    // GPU path: record the layer at full opacity (averaging motion-blur sub-frames additively),
    // run the effect stack, then composite with opacity and blend mode, in After Effects' order.
    val center = if(layer.is3D) scene.fullMatrix(layer,t).map(anchorOffset(layer,t,size))
        else scene.cameraMatrix(scene.cameraAt(t), layer, t)?.map(scene.worldPosition(layer, t)) ?: scene.worldPosition(layer, t)
    val effect = buildRenderEffect(layer.effects, FxContext(size.width, size.height, t, center, layer.color, layer.easing))
    val node = RenderNode("layer")
    node.setPosition(0, 0, ceil(size.width).toInt(), ceil(size.height).toInt())
    val recording = node.beginRecording()
    CanvasDrawScope().draw(this, layoutDirection, Canvas(recording), size) {
        if (samples.size == 1) {
            drawLayerPlain(scene, layer, t, tm, alphaMul = 1f, fullOpacity = true)
        } else {
            val weight = 1f / samples.size
            for (s in samples) {
                drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = weight; blendMode = BlendMode.Plus }) }
                drawLayerPlain(scene, layer, s, tm, alphaMul = 1f, fullOpacity = true)
                drawIntoCanvas { it.restore() }
            }
        }
    }
    node.endRecording()
    node.setRenderEffect(effect)
    node.setAlpha(opacity)
    if (layer.blend != BlendKind.Normal) {
        node.setUseCompositingLayer(true, android.graphics.Paint().apply { blendMode = layer.blend.android() })
    }
    drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
}

private fun BlendKind.android(): android.graphics.BlendMode = when (this) {
    BlendKind.Normal -> android.graphics.BlendMode.SRC_OVER
    BlendKind.Add -> android.graphics.BlendMode.PLUS
    BlendKind.Screen -> android.graphics.BlendMode.SCREEN
    BlendKind.Multiply -> android.graphics.BlendMode.MULTIPLY
    BlendKind.Overlay -> android.graphics.BlendMode.OVERLAY
    BlendKind.Lighten -> android.graphics.BlendMode.LIGHTEN
    BlendKind.Darken -> android.graphics.BlendMode.DARKEN
    BlendKind.Difference -> android.graphics.BlendMode.DIFFERENCE
    BlendKind.ColorDodge -> android.graphics.BlendMode.COLOR_DODGE
}

private fun DrawScope.drawLayerPlain(
    scene: Scene,
    layer: Layer,
    t: Float,
    tm: TextMeasurer,
    alphaMul: Float,
    fullOpacity: Boolean = false,
) {
    val alpha = ((if (fullOpacity) 1f else layer.value(Prop.Opacity, t)) * alphaMul).coerceIn(0f, 1f)
    if (alpha <= 0.001f) return
    val cam = scene.cameraAt(t)
    val camM = scene.cameraMatrix(cam, layer, t)
    when (layer.type) {
        LayerType.Camera, LayerType.Null -> Unit
        LayerType.Gradient -> drawGradient(t, alpha, layer.color)
        LayerType.Particles -> withTransform({ transform(concat(scene.parent(layer, t), camM)) }) {
            drawParticular(layer, t, alpha) { tt -> layerPosition(layer, tt, size) }
        }
        LayerType.Model3D -> withTransform({ transform(concat(scene.parent(layer, t), camM)) }) {
            val s = layerScale(layer, t)
            val orbitX = layer.value(Prop.OrbitX, t) + (if (camM != null && cam != null) cam.orbitX else 0f)
            val orbitY = if (camM != null && cam != null) cam.orbitY else 0f
            drawModel(layer, layerPosition(layer, t, size) - anchorOffset(layer, t, size) * s, s, layer.value(Prop.Rotation, t) + orbitY, orbitX, alpha)
        }
        LayerType.Video, LayerType.Image -> withTransform({ transform(scene.fullMatrix(layer,t)) }) {
            drawFootage(scene, layer, t, alpha)
        }
        LayerType.Text, LayerType.Shape -> withTransform({ transform(scene.fullMatrix(layer,t)) }) {
            val unit = size.minDimension * 0.18f
            val color = layer.color.copy(alpha = alpha)
            if (layer.type == LayerType.Text) drawLayerText(layer, t, color, tm, unit)
            else drawShape(layer.shape, color, unit, layer.has(FxType.Stroke))
        }
    }
}

private fun DrawScope.drawFootage(scene: Scene, layer: Layer, t: Float, alpha: Float) {
    val f = layer.footage ?: return
    val img = scene.frames.frame(f, layer.sourceTime(t)) ?: return
    val fit = footageFit(f, size)
    val stab = layer.stabilize
    clipRect(-fit.width / 2f, -fit.height / 2f, fit.width / 2f, fit.height / 2f) {
        withTransform({
            if (stab != null) {
                // Warp Stabilizer correction, applied in units of the frame's width, then the crop.
                val i = stab.index(f.frameIndex(layer.sourceTime(t)))
                scale(stab.crop, stab.crop, Offset.Zero)
                translate(stab.cRe[i] * fit.width, stab.cIm[i] * fit.width)
                rotate(Math.toDegrees(atan2(stab.mIm[i], stab.mRe[i]).toDouble()).toFloat(), Offset.Zero)
                val s = hypot(stab.mRe[i], stab.mIm[i])
                scale(s, s, Offset.Zero)
            }
        }) {
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset((-fit.width / 2f).roundToInt(), (-fit.height / 2f).roundToInt()),
                dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private fun DrawScope.drawModel(layer: Layer, center: Offset, scale: Float, spin: Float, tilt: Float, alpha: Float) {
    val model = ModelLibrary.byId(layer.modelId)
    withLayerAlpha(alpha) {
        drawScene(
            listOf(RenderItem(model.parts, Transform(rotation = Vec3(0f, spin, 0f)), model.color, layer.id)),
            Camera(yaw = 0f, pitch = (14f + tilt).coerceIn(-85f, 85f), distance = 7f, target = Vec3(0f, 0.2f, 0f)),
            showGrid = false, zoom = scale * 0.9f, center = center, viewExtent = size.minDimension,
        )
    }
}

private inline fun DrawScope.withLayerAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 0.999f) { block(); return }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha }) }
    block()
    drawIntoCanvas { it.restore() }
}

/** A slowly rotating gradient background tinted by the layer colour. */
private fun DrawScope.drawGradient(t: Float, alpha: Float, color: Color) {
    val a = t * 0.35f
    val w = size.width; val h = size.height
    drawRect(
        Brush.linearGradient(
            listOf(lerp(Color.Black, color, 0.42f), lerp(Color.Black, color, 0.16f), Color(0xFF07080C)),
            start = Offset(w / 2 + cos(a) * w, h / 2 + sin(a) * h),
            end = Offset(w / 2 - cos(a) * w, h / 2 - sin(a) * h),
        ),
        alpha = alpha,
    )
    val glow = Offset(w * (0.5f + 0.3f * sin(a * 1.3f)), h * 0.35f)
    val r = max(w, h) * 0.8f
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent), glow, r), r, glow, alpha = alpha)
}

private fun textStyle(unit: Float, density: Float) =
    TextStyle(fontSize = (unit * 0.42f / density).sp, fontWeight = FontWeight.Black, letterSpacing = (unit * 0.02f / density).sp)

private fun DrawScope.drawCenteredText(text: String, color: Color, tm: TextMeasurer, unit: Float) {
    if (text.isBlank()) return
    // Measured without a colour so the TextMeasurer cache hits for every echo copy.
    val layout = tm.measure(text, textStyle(unit, density), density = this)
    drawText(layout, color = color, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
}

/** Text with its text animators applied character by character (Range Selector, Typewriter, Wave, Tracking). */
private fun DrawScope.drawLayerText(layer: Layer, t: Float, color: Color, tm: TextMeasurer, unit: Float) {
    val anim = layer.fx(FxType.TextAnimator); val writer = layer.fx(FxType.Typewriter)
    val wave = layer.fx(FxType.TextWave); val tracking = layer.fx(FxType.Tracking)
    if (anim == null && writer == null && wave == null && tracking == null) { drawCenteredText(layer.text, color, tm, unit); return }
    val text = layer.text
    if (text.isBlank()) return
    val e = layer.easing
    val spacing = tracking?.param(0, t, e) ?: 0f
    val base = textStyle(unit, density)
    val style = base.copy(letterSpacing = ((unit * 0.02f + spacing * unit * 0.42f) / density).sp)
    val layout = tm.measure(text, style, density = this)
    val ox = -layout.size.width / 2f; val oy = -layout.size.height / 2f
    val n = text.length
    val shown = writer?.let { floor(it.param(0, t, e).coerceIn(0f, 1f) * n).toInt() } ?: n
    var caretX = ox
    for (i in 0 until min(shown, n)) {
        val box = layout.getBoundingBox(i)
        caretX = ox + box.right
        if (text[i].isWhitespace()) continue
        var reveal = 1f
        if (anim != null) {
            val spread = anim.param(4, t, e).coerceAtLeast(1f)
            val r = ((anim.param(0, t, e) * (n + spread) - i) / spread).coerceIn(0f, 1f)
            reveal = 1f - (1f - r) * (1f - r) * (1f - r)
        }
        if (reveal <= 0.001f) continue
        var dy = 0f; var sc = 1f; var rot = 0f
        if (anim != null) {
            dy += (1f - reveal) * anim.param(1, t, e) * unit
            sc = anim.param(2, t, e) + (1f - anim.param(2, t, e)) * reveal
            rot = (1f - reveal) * anim.param(3, t, e)
        }
        if (wave != null) {
            val phase = 2f * PI.toFloat() * wave.param(1, t, e) * i / max(1, n) * 2f + t * wave.param(2, t, e)
            dy += wave.param(0, t, e) * unit * 0.6f * sin(phase)
        }
        // A lone glyph is drawn centred on its box, where letter spacing has no effect: measuring it with
        // the base style keeps the cache hitting while Tracking animates.
        val glyph = tm.measure(text[i].toString(), base, density = this)
        withTransform({ translate(ox + box.center.x, oy + box.center.y + dy); rotate(rot, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
            drawText(glyph, color = color.copy(alpha = color.alpha * reveal), topLeft = Offset(-glyph.size.width / 2f, -glyph.size.height / 2f))
        }
    }
    if (writer != null && writer.param(1, t, e) > 0.5f && (t * 2f).toInt() % 2 == 0) {
        val caret = tm.measure("|", base, density = this)
        drawText(caret, color = color, topLeft = Offset(caretX + unit * 0.03f, oy + (layout.size.height - caret.size.height) / 2f))
    }
}

fun DrawScope.drawShape(kind: ShapeKind, color: Color, unit: Float, outline: Boolean) {
    val style = if (outline) Stroke(unit * 0.06f) else null
    when (kind) {
        ShapeKind.Circle -> if (style != null) drawCircle(color, unit, Offset.Zero, style = style) else drawCircle(color, unit, Offset.Zero)
        ShapeKind.Rect -> {
            val tl = Offset(-unit, -unit); val sz = Size(unit * 2, unit * 2); val cr = CornerRadius(unit * 0.25f)
            if (style != null) drawRoundRect(color, tl, sz, cr, style = style) else drawRoundRect(color, tl, sz, cr)
        }
        ShapeKind.Ring -> {
            drawCircle(color, unit, Offset.Zero, style = Stroke(unit * 0.09f))
            drawArc(color, 20f, 80f, false, Offset(-unit * 1.25f, -unit * 1.25f), Size(unit * 2.5f, unit * 2.5f), style = Stroke(unit * 0.04f))
            drawArc(color, 200f, 80f, false, Offset(-unit * 1.25f, -unit * 1.25f), Size(unit * 2.5f, unit * 2.5f), style = Stroke(unit * 0.04f))
        }
        ShapeKind.Star, ShapeKind.Triangle -> {
            val path = Path()
            val points = if (kind == ShapeKind.Star) 10 else 3
            for (i in 0 until points) {
                val ang = -PI / 2 + i * 2 * PI / points
                val rad = if (kind == ShapeKind.Star && i % 2 == 1) unit * 0.45f else unit
                val p = Offset((cos(ang) * rad).toFloat(), (sin(ang) * rad).toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            if (style != null) drawPath(path, color, style = style) else drawPath(path, color)
        }
    }
}

// ---------------------------------------------------------------- Selection & picking

/** Half extents of a layer's content in its own (unscaled) space. */
private fun localHalfSize(layer: Layer, size: Size, tm: TextMeasurer, density: Density): Size {
    // Content scales with the frame's short side, so a 9:16 comp keeps the proportions of 16:9.
    val unit = size.minDimension * 0.18f
    return when (layer.type) {
        LayerType.Text -> {
            if (layer.text.isBlank()) Size(unit * 0.3f, unit * 0.3f)
            else tm.measure(layer.text, textStyle(unit, density.density), density = density).size.let { Size(it.width / 2f, it.height / 2f) }
        }
        LayerType.Shape -> if (layer.shape == ShapeKind.Ring) Size(unit * 1.25f, unit * 1.25f) else Size(unit, unit)
        LayerType.Model3D -> Size(size.minDimension * 0.2f, size.minDimension * 0.3f)
        LayerType.Null -> Size(size.minDimension * 0.045f, size.minDimension * 0.045f)
        LayerType.Video, LayerType.Image -> layer.footage?.let { footageFit(it, size) }?.let { Size(it.width / 2f, it.height / 2f) } ?: Size(unit, unit)
        LayerType.Particles, LayerType.Gradient, LayerType.Camera -> Size(size.width / 2f, size.height / 2f)
    }
}

/** After Effects-style bounding box with corner handles and the anchor-point crosshair. */
private fun DrawScope.drawSelection(scene: Scene, layer: Layer, t: Float, tm: TextMeasurer) {
    if (layer.type == LayerType.Camera || layer.type == LayerType.Particles) return
    val half = localHalfSize(layer, size, tm, this)
    val camM = scene.cameraMatrix(scene.cameraAt(t), layer, t)
    if (layer.type == LayerType.Model3D) {
        val s = layerScale(layer, t).coerceAtLeast(0.05f)
        val c = layerPosition(layer, t, size) - anchorOffset(layer, t, size) * s
        withTransform({ transform(concat(scene.parent(layer, t), camM)) }) {
            drawBox(Rect(c - Offset(half.width * s, half.height * s), Size(half.width * 2 * s, half.height * 2 * s)), 1f, null)
        }
        return
    }
    val m = scene.fullMatrix(layer,t)
    // Undo the layer's scale for stroke widths and handle sizes, so they stay crisp at any zoom.
    val scale = hypot(m[0, 0], m[0, 1]).coerceAtLeast(0.05f)
    withTransform({ transform(m) }) {
        drawBox(Rect(Offset(-half.width, -half.height), Size(half.width * 2, half.height * 2)), 1f / scale, anchorOffset(layer, t, size))
    }
}

private fun DrawScope.drawBox(r: Rect, k: Float, anchor: Offset?) {
    drawRect(SelectionColor, r.topLeft, r.size, style = Stroke(1.5f * k))
    val hs = 4f * k
    for (p in listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight, r.topCenter, r.bottomCenter, r.centerLeft, r.centerRight)) {
        drawRect(Color.White, Offset(p.x - hs, p.y - hs), Size(hs * 2, hs * 2))
        drawRect(SelectionColor, Offset(p.x - hs, p.y - hs), Size(hs * 2, hs * 2), style = Stroke(1.2f * k))
    }
    if (anchor != null) {
        drawCircle(SelectionColor, 5f * k, anchor, style = Stroke(1.2f * k))
        drawLine(SelectionColor, Offset(anchor.x - 9f * k, anchor.y), Offset(anchor.x + 9f * k, anchor.y), 1.2f * k)
        drawLine(SelectionColor, Offset(anchor.x, anchor.y - 9f * k), Offset(anchor.x, anchor.y + 9f * k), 1.2f * k)
    }
}

/** Topmost selectable layer under [point], tested against its transformed bounds (camera included). */
fun pickLayer(layers: List<Layer>, t: Float, point: Offset, size: Size, tm: TextMeasurer, density: Density): Int? {
    val scene = Scene(layers, size, FootageFrames, FPS)
    val anySolo = layers.any { it.solo && it.type != LayerType.Null && it.type != LayerType.Camera }
    for (layer in scene.space(t).drawOrder().asReversed()) {
        if (!layer.activeAt(t) || layer.locked || (anySolo && !layer.solo)) continue
        if (layer.type == LayerType.Gradient || layer.type == LayerType.Particles || layer.type == LayerType.Camera || layer.type == LayerType.Null) continue
        val half = localHalfSize(layer, size, tm, density)
        val camM = scene.cameraMatrix(scene.cameraAt(t), layer, t)
        val local: Offset
        val hx: Float
        val hy: Float
        if (layer.type == LayerType.Model3D) {
            val s = layerScale(layer, t)
            val inv = inverseOrNull(concat(scene.parent(layer, t), camM)) ?: continue
            local = inv.map(point) - (layerPosition(layer, t, size) - anchorOffset(layer, t, size) * s)
            hx = half.width * s; hy = half.height * s
        } else {
            val inv = inverseOrNull(scene.fullMatrix(layer,t)) ?: continue
            local = inv.map(point)
            hx = half.width; hy = half.height
        }
        if (local.x.isNaN()) continue
        if (local.x in -hx..hx && local.y in -hy..hy) return layer.id
    }
    return null
}

/** Screen position of normalised footage coordinates (0–1) for a footage layer, camera included. */
fun footageToScreen(layers: List<Layer>, layerId: Int, t: Float, size: Size, u: Float, v: Float): Offset? {
    val scene = Scene(layers, size, FootageFrames, FPS)
    val layer = scene.byId[layerId] ?: return null
    val f = layer.footage ?: return null
    val fit = footageFit(f, size)
    return scene.fullMatrix(layer, t).map(Offset((u - 0.5f) * fit.width, (v - 0.5f) * fit.height))
}

/** Normalised footage coordinates (0–1) under a screen point, or null outside the frame. */
fun screenToFootage(layers: List<Layer>, layerId: Int, t: Float, size: Size, point: Offset): Offset? {
    val scene = Scene(layers, size, FootageFrames, FPS)
    val layer = scene.byId[layerId] ?: return null
    val f = layer.footage ?: return null
    val fit = footageFit(f, size)
    val local = (inverseOrNull(scene.fullMatrix(layer, t)) ?: return null).map(point)
    val u = local.x / fit.width + 0.5f; val v = local.y / fit.height + 0.5f
    return if (u in 0f..1f && v in 0f..1f) Offset(u, v) else null
}

