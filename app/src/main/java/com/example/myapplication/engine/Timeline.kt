package com.example.myapplication.engine

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

// Data model for the motion-graphics editor: layers with keyframed properties, After Effects style.

/**
 * Transform properties in After Effects order. Position Z gives 2D layers depth for the comp
 * camera (parallax); Orbit X/Y tilt the view of 3D models and the camera.
 */
enum class Prop(val label: String, val min: Float, val max: Float, val default: Float, val unit: String) {
    AnchorX("Anchor Point X", -1f, 1f, 0f, ""),
    AnchorY("Anchor Point Y", -1f, 1f, 0f, ""),
    PosX("Position X", -1.2f, 1.2f, 0f, ""),
    PosY("Position Y", -1.2f, 1.2f, 0f, ""),
    PosZ("Position Z", -0.8f, 4f, 0f, ""),
    Scale("Scale", 0f, 3f, 1f, "×"),
    Rotation("Rotation", -360f, 360f, 0f, "°"),
    OrbitX("Orbit X", -80f, 80f, 0f, "°"),
    OrbitY("Orbit Y", -180f, 180f, 0f, "°"),
    Opacity("Opacity", 0f, 1f, 1f, ""),
}

/** The transform properties a layer type shows, in panel order. */
fun LayerType.transformProps(): List<Prop> = when (this) {
    LayerType.Null -> listOf(Prop.AnchorX, Prop.AnchorY, Prop.PosX, Prop.PosY, Prop.Scale, Prop.Rotation)
    LayerType.Camera -> listOf(Prop.PosX, Prop.PosY, Prop.PosZ, Prop.Scale, Prop.Rotation, Prop.OrbitX, Prop.OrbitY)
    LayerType.Gradient -> listOf(Prop.Opacity)
    LayerType.Particles -> listOf(Prop.PosX, Prop.PosY, Prop.PosZ, Prop.Opacity)
    LayerType.Model3D -> listOf(Prop.AnchorX, Prop.AnchorY, Prop.PosX, Prop.PosY, Prop.PosZ, Prop.Scale, Prop.Rotation, Prop.OrbitX, Prop.Opacity)
    else -> listOf(Prop.AnchorX, Prop.AnchorY, Prop.PosX, Prop.PosY, Prop.PosZ, Prop.Scale, Prop.Rotation, Prop.Opacity)
}

/** Camera layers reuse the transform tracks under After Effects' camera names. */
fun Prop.labelFor(type: LayerType): String = when {
    type == LayerType.Camera && this == Prop.PosX -> "Point of Interest X"
    type == LayerType.Camera && this == Prop.PosY -> "Point of Interest Y"
    type == LayerType.Camera && this == Prop.Scale -> "Zoom"
    type == LayerType.Camera && this == Prop.Rotation -> "Roll"
    type == LayerType.Model3D && this == Prop.Rotation -> "Y Rotation"
    type == LayerType.Model3D && this == Prop.OrbitX -> "X Rotation"
    else -> label
}

/**
 * Keyframe interpolation. Linear, the Easy Ease family and Smooth are After Effects temporal
 * bezier presets; Bezier means the keys' own influence/speed handles; Hold, Back, Elastic and
 * Bounce are curve presets.
 */
enum class Easing(val label: String) {
    Linear("Linear"), EaseInOut("Easy Ease"), EaseIn("Easy Ease In"), EaseOut("Easy Ease Out"), Smooth("Smooth"),
    Bezier("Bezier"), Hold("Hold"), Back("Back"), Elastic("Elastic"), Bounce("Bounce"),
}

/**
 * A keyframe. [easing] shapes the segment leaving this key (null = the layer default). When that
 * segment is [Easing.Bezier] it uses this key's out-handle and the next key's in-handle:
 * influence is the share of the segment's duration (0–1), speed is value units per second.
 */
data class Keyframe(
    val time: Float,
    val value: Float,
    val easing: Easing? = null,
    val inInfluence: Float = 1f / 3f,
    val inSpeed: Float = 0f,
    val outInfluence: Float = 1f / 3f,
    val outSpeed: Float = 0f,
)

/** Influence/speed handle pair for one side of a key. */
data class Handle(val influence: Float, val speed: Float)

/**
 * The handles that reproduce a segment's current curve, so presets can be turned into editable
 * bezier handles without changing the motion. Null for curves a bezier can't express.
 */
fun segmentHandles(a: Keyframe, b: Keyframe, easing: Easing): Pair<Handle, Handle>? {
    val dt = (b.time - a.time).takeIf { it > 0f } ?: return null
    val slope = (b.value - a.value) / dt
    return when (easing) {
        Easing.Linear -> Handle(1f / 6f, slope) to Handle(1f / 6f, slope)
        Easing.EaseInOut -> Handle(1f / 3f, 0f) to Handle(1f / 3f, 0f)
        Easing.Smooth -> Handle(0.75f, 0f) to Handle(0.75f, 0f)
        Easing.EaseIn -> Handle(1f / 6f, slope) to Handle(1f / 3f, 0f)
        Easing.EaseOut -> Handle(1f / 3f, 0f) to Handle(1f / 6f, slope)
        Easing.Bezier -> Handle(a.outInfluence, a.outSpeed) to Handle(b.inInfluence, b.inSpeed)
        else -> null
    }
}

/** A property's animation. [loop] repeats the keyed segment after the last key, like loopOut("cycle"). */
data class Track(val static: Float, val keys: List<Keyframe> = emptyList(), val loop: Boolean = false) {
    val animated: Boolean get() = keys.isNotEmpty()

    fun valueAt(time: Float, default: Easing): Float {
        if (keys.isEmpty()) return static
        var t = time
        val first = keys.first(); val last = keys.last()
        val period = last.time - first.time
        if (loop && keys.size >= 2 && period > KEY_EPS && t > last.time) t = first.time + (t - first.time) % period
        if (t <= first.time) return first.value
        if (t >= last.time) return last.value
        var i = 0
        while (i < keys.size - 2 && keys[i + 1].time <= t) i++
        return segmentValue(keys[i], keys[i + 1], t, keys[i].easing ?: default)
    }

    /** Writes [value] at [t]: a keyframe when animated (keeping an existing key's interpolation), the static value otherwise. */
    fun withValueAt(t: Float, value: Float): Track {
        if (keys.isEmpty()) return copy(static = value)
        val existing = keys.firstOrNull { abs(it.time - t) < KEY_EPS }
        val others = keys.filterNot { it === existing }
        return copy(keys = (others + (existing?.copy(time = t, value = value) ?: Keyframe(t, value))).sortedBy { it.time })
    }

    fun hasKeyAt(t: Float) = keys.any { abs(it.time - t) < KEY_EPS }

    fun shifted(dt: Float) = copy(keys = keys.map { it.copy(time = it.time + dt) })
}

/** Value between keys [a] and [b] at time [t]. */
fun segmentValue(a: Keyframe, b: Keyframe, t: Float, easing: Easing): Float {
    val dt = b.time - a.time
    if (dt <= 0f) return b.value
    val u = ((t - a.time) / dt).coerceIn(0f, 1f)
    if (easing == Easing.Bezier) {
        // After Effects temporal bezier: control points sit at influence × duration, sloped by speed.
        val out = a.outInfluence.coerceIn(0.001f, 1f)
        val incoming = b.inInfluence.coerceIn(0.001f, 1f)
        val x1 = out
        val x2 = 1f - incoming
        val y1 = a.value + a.outSpeed * out * dt
        val y2 = b.value - b.inSpeed * incoming * dt
        return cubic(a.value, y1, y2, b.value, solveBezierX(x1, x2, u))
    }
    return a.value + (b.value - a.value) * ease(u, easing)
}

private fun cubic(p0: Float, p1: Float, p2: Float, p3: Float, s: Float): Float {
    val r = 1f - s
    return r * r * r * p0 + 3f * r * r * s * p1 + 3f * r * s * s * p2 + s * s * s * p3
}

/** Finds the bezier parameter whose x equals [u]; x(s) is monotonic for control x in [0, 1]. */
private fun solveBezierX(x1: Float, x2: Float, u: Float): Float {
    var lo = 0f; var hi = 1f
    var s = u
    repeat(14) {
        val x = cubic(0f, x1, x2, 1f, s)
        if (x < u) lo = s else hi = s
        s = (lo + hi) / 2f
    }
    return s
}

/** Normalised cubic-bezier easing through (0,0), (x1,y1), (x2,y2), (1,1), like CSS cubic-bezier(). */
fun bezierEase(x1: Float, y1: Float, x2: Float, y2: Float, u: Float): Float = cubic(0f, y1, y2, 1f, solveBezierX(x1, x2, u))

fun ease(u: Float, easing: Easing): Float = when (easing) {
    Easing.Linear, Easing.Bezier -> u
    // Easy Ease (F9): 33.33% influence and zero speed on both sides.
    Easing.EaseInOut -> bezierEase(1f / 3f, 0f, 2f / 3f, 1f, u)
    // Easy Ease In: slows into the next key. Easy Ease Out: eases away from this key.
    Easing.EaseIn -> bezierEase(1f / 6f, 1f / 6f, 2f / 3f, 1f, u)
    Easing.EaseOut -> bezierEase(1f / 3f, 0f, 5f / 6f, 5f / 6f, u)
    // The long, soft ease motion designers call "smooth": 75% influence both sides.
    Easing.Smooth -> bezierEase(0.75f, 0f, 0.25f, 1f, u)
    Easing.Hold -> if (u >= 1f) 1f else 0f
    Easing.Back -> {
        val c = 1.70158f * 1.525f
        if (u < 0.5f) ((2 * u).pow(2) * ((c + 1) * 2 * u - c)) / 2
        else ((2 * u - 2).pow(2) * ((c + 1) * (u * 2 - 2) + c) + 2) / 2
    }
    Easing.Elastic -> when (u) {
        0f -> 0f
        1f -> 1f
        else -> (2.0.pow(-10.0 * u) * sin((u * 10 - 0.75) * (2 * PI / 3)) + 1).toFloat()
    }
    Easing.Bounce -> {
        val n = 7.5625f; val d = 2.75f
        var x = u
        when {
            x < 1 / d -> n * x * x
            x < 2 / d -> { x -= 1.5f / d; n * x * x + 0.75f }
            x < 2.5f / d -> { x -= 2.25f / d; n * x * x + 0.9375f }
            else -> { x -= 2.625f / d; n * x * x + 0.984375f }
        }
    }
}

/** Frame rate of the built-in templates and thumbnails; projects carry their own. */
const val FPS = 30
const val FRAME = 1f / FPS

/** Two keys closer than this are the same key: half a frame at the highest supported rate (120 fps). */
const val KEY_EPS = 1f / 240f

enum class LayerType(val label: String) {
    Text("Teks"), Shape("Shape"), Model3D("Model 3D"), Gradient("Gradien"), Particles("Partikel"),
    Camera("Kamera"), Video("Video"), Image("Foto"), Null("Null Object"),
}

enum class ShapeKind(val label: String) { Circle("Lingkaran"), Rect("Kotak"), Star("Bintang"), Triangle("Segitiga"), Ring("Cincin") }

/** Layer blending, the "Mode" column of After Effects' timeline. */
enum class BlendKind(val label: String, val mode: BlendMode) {
    Normal("Normal", BlendMode.SrcOver), Add("Add", BlendMode.Plus), Screen("Screen", BlendMode.Screen),
    Multiply("Multiply", BlendMode.Multiply), Overlay("Overlay", BlendMode.Overlay), Lighten("Lighten", BlendMode.Lighten),
    Darken("Darken", BlendMode.Darken), Difference("Difference", BlendMode.Difference), ColorDodge("Color Dodge", BlendMode.ColorDodge),
}

// ---------------------------------------------------------------- Effects

enum class FxCategory(val label: String) {
    Blur("Blur & Sharpen"), Color("Color Correction"), Distort("Distort"), Stylize("Stylize"), Generate("Generate"),
    Keying("Keying"),
    Transition("Transition"), Time("Time"), Text("Text"), Particles("Particles"), Expression("Expression"),
}

data class FxParam(val label: String, val min: Float, val max: Float, val default: Float, val unit: String = "")

/** Effects named after their After Effects counterparts; every parameter can be keyframed. */
enum class FxType(val label: String, val category: FxCategory, val description: String, val params: List<FxParam> = emptyList()) {
    MotionBlur("Motion Blur", FxCategory.Blur, "Blur dari kecepatan layer, dihitung per sub-frame seperti AE",
        listOf(FxParam("Shutter Angle", 0f, 720f, 180f, "°"), FxParam("Samples", 2f, 16f, 8f))),
    GaussianBlur("Gaussian Blur", FxCategory.Blur, "Kaburkan layer secara merata",
        listOf(FxParam("Blurriness", 0f, 80f, 14f, "px"))),
    DirectionalBlur("Directional Blur", FxCategory.Blur, "Blur searah, kesan gerak cepat",
        listOf(FxParam("Direction", 0f, 360f, 0f, "°"), FxParam("Blur Length", 0f, 120f, 30f, "px"))),
    RadialBlur("Radial Blur", FxCategory.Blur, "Blur zoom dari tengah frame",
        listOf(FxParam("Amount", 0f, 1f, 0.35f))),
    Sharpen("Sharpen", FxCategory.Blur, "Pertegas tepi",
        listOf(FxParam("Sharpen Amount", 0f, 3f, 1f))),

    BrightnessContrast("Brightness & Contrast", FxCategory.Color, "Atur terang dan kontras",
        listOf(FxParam("Brightness", -1f, 1f, 0.1f), FxParam("Contrast", -1f, 1f, 0.25f))),
    Exposure("Exposure", FxCategory.Color, "Terangkan dalam stop, plus gamma",
        listOf(FxParam("Exposure", -3f, 3f, 0.6f), FxParam("Gamma", 0.2f, 3f, 1f))),
    Levels("Levels", FxCategory.Color, "Titik hitam, titik putih, dan gamma",
        listOf(FxParam("Input Black", 0f, 0.9f, 0.08f), FxParam("Input White", 0.1f, 1f, 0.92f), FxParam("Gamma", 0.2f, 3f, 1f))),
    HueSaturation("Hue/Saturation", FxCategory.Color, "Geser hue dan saturasi",
        listOf(FxParam("Master Hue", -180f, 180f, 40f, "°"), FxParam("Master Saturation", -1f, 1f, 0.2f))),
    ColorCycle("Color Cycle", FxCategory.Color, "Hue berputar terus, efek pelangi",
        listOf(FxParam("Speed", 0f, 3f, 0.5f), FxParam("Saturation", 0f, 2f, 1.3f))),
    Tint("Tint", FxCategory.Color, "Petakan gelap-terang ke warna layer",
        listOf(FxParam("Amount to Tint", 0f, 1f, 1f))),
    Duotone("Duotone", FxCategory.Color, "Bayangan gelap, bagian terang jadi warna layer",
        listOf(FxParam("Amount", 0f, 1f, 1f))),
    BlackWhite("Black & White", FxCategory.Color, "Hitam putih",
        listOf(FxParam("Amount", 0f, 1f, 1f))),
    Threshold("Threshold", FxCategory.Color, "Hitam putih tegas",
        listOf(FxParam("Level", 0f, 1f, 0.5f))),
    Invert("Invert", FxCategory.Color, "Balik warna",
        listOf(FxParam("Blend With Original", 0f, 1f, 0f))),
    Posterize("Posterize", FxCategory.Color, "Kurangi jumlah tingkat warna",
        listOf(FxParam("Level", 2f, 16f, 5f))),
    ColorBalance("Color Balance", FxCategory.Color, "Koreksi kanal merah, hijau dan biru secara terpisah",
        listOf(FxParam("Red",-.5f,.5f,0f),FxParam("Green",-.5f,.5f,0f),FxParam("Blue",-.5f,.5f,0f))),
    ChromaKey("Chroma Key", FxCategory.Keying, "Hapus warna latar dengan feather dan pengurangan spill",
        listOf(FxParam("Key Hue",0f,360f,120f,"°"),FxParam("Threshold",0f,1f,.25f),FxParam("Softness",.001f,.5f,.08f),FxParam("Despill",0f,1f,.7f))),
    LumaKey("Luma Key", FxCategory.Keying, "Hapus area gelap atau terang berdasarkan luminance",
        listOf(FxParam("Threshold",0f,1f,.25f),FxParam("Softness",.001f,.5f,.05f),FxParam("Invert",0f,1f,0f))),

    WaveWarp("Wave Warp", FxCategory.Distort, "Gelombang yang bergerak",
        listOf(FxParam("Wave Height", 0f, 80f, 12f, "px"), FxParam("Wave Width", 10f, 400f, 120f, "px"), FxParam("Wave Speed", 0f, 10f, 2f))),
    Ripple("Ripple", FxCategory.Distort, "Riak air dari tengah layer",
        listOf(FxParam("Amplitude", 0f, 40f, 10f, "px"), FxParam("Wavelength", 10f, 200f, 50f, "px"), FxParam("Speed", 0f, 10f, 3f))),
    Twirl("Twirl", FxCategory.Distort, "Pelintir dari tengah",
        listOf(FxParam("Angle", -720f, 720f, 180f, "°"), FxParam("Twirl Radius", 0.05f, 1.5f, 0.5f))),
    Bulge("Bulge", FxCategory.Distort, "Gembungkan atau cekungkan",
        listOf(FxParam("Bulge Height", -1f, 1f, 0.6f), FxParam("Radius", 0.05f, 1.5f, 0.5f))),
    TurbulentDisplace("Turbulent Displace", FxCategory.Distort, "Distorsi noise organik",
        listOf(FxParam("Amount", 0f, 80f, 18f, "px"), FxParam("Size", 10f, 300f, 80f, "px"), FxParam("Evolution Speed", 0f, 5f, 1f))),
    Mirror("Mirror", FxCategory.Distort, "Cermin di garis yang melewati tengah layer",
        listOf(FxParam("Reflection Angle", 0f, 360f, 90f, "°"))),
    Kaleidoscope("Kaleidoscope", FxCategory.Distort, "Pola kaleidoskop seperti CC Kaleida",
        listOf(FxParam("Segments", 2f, 16f, 6f), FxParam("Rotation", -180f, 180f, 0f, "°"))),
    PolarCoordinates("Polar Coordinates", FxCategory.Distort, "Gulung layer jadi lingkaran",
        listOf(FxParam("Interpolation", 0f, 1f, 1f))),
    MotionTile("Motion Tile", FxCategory.Distort, "Ulang bagian tengah layer jadi pola",
        listOf(FxParam("Tile Size", 0.1f, 1f, 0.5f), FxParam("Mirror Edges", 0f, 1f, 1f))),
    Shake("Shake", FxCategory.Distort, "Guncangan kamera acak untuk beat",
        listOf(FxParam("Amplitude", 0f, 80f, 18f, "px"), FxParam("Frequency", 0f, 20f, 8f), FxParam("Rotation", 0f, 20f, 3f, "°"), FxParam("Zoom", 0f, 0.3f, 0.04f))),

    Glow("Glow", FxCategory.Stylize, "Pendar dari bagian terang",
        listOf(FxParam("Glow Radius", 0f, 80f, 22f, "px"), FxParam("Glow Intensity", 0f, 4f, 1.4f))),
    DeepGlow("Deep Glow", FxCategory.Stylize, "Glow berlapis yang lembut dan terang",
        listOf(FxParam("Radius", 0f, 120f, 40f, "px"), FxParam("Exposure", 0f, 3f, 1.1f))),
    LightRays("Light Rays", FxCategory.Stylize, "Sinar memancar dari bagian terang",
        listOf(FxParam("Intensity", 0f, 2f, 1f), FxParam("Length", 0f, 1f, 0.45f))),
    Shine("Shine", FxCategory.Stylize, "Kilau cahaya yang menyapu layer",
        listOf(FxParam("Speed", 0f, 3f, 0.6f), FxParam("Width", 0.02f, 0.5f, 0.12f), FxParam("Angle", 0f, 180f, 30f, "°"), FxParam("Intensity", 0f, 3f, 1.5f))),
    DropShadow("Drop Shadow", FxCategory.Stylize, "Bayangan lembut",
        listOf(FxParam("Distance", 0f, 80f, 14f, "px"), FxParam("Softness", 0f, 60f, 12f, "px"), FxParam("Opacity", 0f, 1f, 0.6f))),
    Mosaic("Mosaic", FxCategory.Stylize, "Pikselasi berblok",
        listOf(FxParam("Block Size", 2f, 80f, 14f, "px"))),
    Halftone("Halftone", FxCategory.Stylize, "Titik cetak seperti koran",
        listOf(FxParam("Dot Size", 3f, 40f, 8f, "px"))),
    Stroke("Stroke", FxCategory.Stylize, "Hanya garis tepi (shape)"),
    FindEdges("Find Edges", FxCategory.Stylize, "Garis tepi menyala seperti sketsa",
        listOf(FxParam("Intensity", 0f, 3f, 1.2f))),
    Emboss("Emboss", FxCategory.Stylize, "Relief timbul",
        listOf(FxParam("Strength", 0f, 4f, 1.5f))),
    ChromaticAberration("RGB Split", FxCategory.Stylize, "Pisah kanal merah dan biru",
        listOf(FxParam("Offset", 0f, 40f, 8f, "px"))),
    Glitch("Glitch", FxCategory.Stylize, "Blok tergeser dan kanal pecah",
        listOf(FxParam("Intensity", 0f, 1f, 0.5f))),
    Vhs("VHS", FxCategory.Stylize, "Garis scan, warna luntur, pita goyang",
        listOf(FxParam("Amount", 0f, 1f, 0.6f))),
    Noise("Noise", FxCategory.Stylize, "Grain film",
        listOf(FxParam("Amount of Noise", 0f, 1f, 0.3f))),
    Strobe("Strobe Light", FxCategory.Stylize, "Kedip terang cepat",
        listOf(FxParam("Frequency", 0f, 20f, 6f), FxParam("Intensity", 0f, 1f, 0.7f))),
    Vignette("Vignette", FxCategory.Stylize, "Gelapkan tepi",
        listOf(FxParam("Amount", 0f, 1f, 0.6f))),

    FractalNoise("Fractal Noise", FxCategory.Generate, "Tekstur noise bergerak di dalam layer",
        listOf(FxParam("Scale", 10f, 400f, 120f, "px"), FxParam("Contrast", 0f, 3f, 1.2f), FxParam("Evolution", 0f, 3f, 0.5f))),
    GradientRamp("Gradient Ramp", FxCategory.Generate, "Gradasi warna layer ke gelap",
        listOf(FxParam("Ramp Angle", 0f, 360f, 90f, "°"), FxParam("Blend", 0f, 1f, 1f))),
    OpticalFlares("Optical Flares", FxCategory.Generate, "Cahaya lensa: inti, halo, ghost, dan garis anamorfik",
        listOf(FxParam("Intensity",0f,5f,1.4f),FxParam("Position X",-1f,1f,0f),FxParam("Position Y",-1f,1f,0f),
            FxParam("Size",.1f,3f,1f),FxParam("Streak",0f,3f,1f),FxParam("Ghosts",0f,2f,.7f),FxParam("Hue",0f,360f,35f,"°"))),
    LensFlare("Lens Flare", FxCategory.Generate, "Suar lensa di dekat layer",
        listOf(FxParam("Brightness", 0f, 3f, 1.2f), FxParam("Offset X", -1f, 1f, 0.3f), FxParam("Offset Y", -1f, 1f, -0.25f))),
    LightLeak("Light Leak", FxCategory.Generate, "Bocoran cahaya hangat yang bergerak",
        listOf(FxParam("Intensity", 0f, 2f, 0.8f), FxParam("Speed", 0f, 2f, 0.4f))),

    LinearWipe("Linear Wipe", FxCategory.Transition, "Sapu lurus",
        listOf(FxParam("Transition Completion", 0f, 1f, 0.3f), FxParam("Wipe Angle", 0f, 360f, 90f, "°"), FxParam("Feather", 0f, 0.5f, 0.05f))),
    RadialWipe("Radial Wipe", FxCategory.Transition, "Sapu melingkar seperti jam",
        listOf(FxParam("Transition Completion", 0f, 1f, 0.3f))),
    IrisWipe("Iris Wipe", FxCategory.Transition, "Bukaan lingkaran dari tengah layer",
        listOf(FxParam("Transition Completion", 0f, 1f, 0.3f), FxParam("Feather", 0f, 0.3f, 0.04f))),
    VenetianBlinds("Venetian Blinds", FxCategory.Transition, "Tirai bergaris",
        listOf(FxParam("Transition Completion", 0f, 1f, 0.3f), FxParam("Width", 2f, 40f, 12f))),
    BlockDissolve("Block Dissolve", FxCategory.Transition, "Larut per blok acak",
        listOf(FxParam("Transition Completion", 0f, 1f, 0.3f), FxParam("Block Size", 2f, 80f, 16f, "px"))),

    Echo("Echo", FxCategory.Time, "Salinan tertunda di belakang gerakan",
        listOf(FxParam("Number Of Echoes", 1f, 8f, 5f), FxParam("Echo Time", 0.01f, 0.25f, 0.05f, "s"))),

    TextAnimator("Text Animator", FxCategory.Text, "Huruf muncul satu per satu (Range Selector)",
        listOf(FxParam("Progress", 0f, 1f, 1f), FxParam("Offset Y", -2f, 2f, 0.6f), FxParam("Scale From", 0f, 3f, 0.3f), FxParam("Rotation From", -180f, 180f, 0f, "°"), FxParam("Spread", 1f, 20f, 4f))),
    Typewriter("Typewriter", FxCategory.Text, "Huruf diketik satu per satu, dengan kursor",
        listOf(FxParam("Progress", 0f, 1f, 1f), FxParam("Cursor", 0f, 1f, 1f))),
    TextWave("Wave Text", FxCategory.Text, "Huruf naik turun bergelombang",
        listOf(FxParam("Amplitude", 0f, 1f, 0.25f), FxParam("Frequency", 0f, 3f, 1f), FxParam("Speed", 0f, 10f, 4f))),
    Tracking("Tracking", FxCategory.Text, "Jarak antar huruf",
        listOf(FxParam("Tracking Amount", -0.2f, 1.5f, 0.3f))),

    Particular("Particular", FxCategory.Particles, "Emitter, fisika, turbulensi, glow, dan streak seperti Trapcode Particular",
        listOf(
            FxParam("Particles/sec", 0f, 300f, 40f), FxParam("Life", 0.2f, 10f, 3f, "s"), FxParam("Velocity", 0f, 2f, 0.25f),
            FxParam("Direction", 0f, 360f, 270f, "°"), FxParam("Spread", 0f, 360f, 30f, "°"), FxParam("Gravity", -1f, 1f, 0f),
            FxParam("Turbulence", 0f, 1f, 0.35f), FxParam("Size", 0.5f, 40f, 6f, "px"), FxParam("Size Random", 0f, 1f, 0.6f),
            FxParam("Emitter Size", 0f, 2.5f, 0.3f), FxParam("Emitter Type", 0f, 2f, 0f), FxParam("Glow", 0f, 1f, 0.7f),
            FxParam("Streak", 0f, 1f, 0f), FxParam("Opacity", 0f, 1f, 0.9f), FxParam("Color Random", 0f, 1f, 0.3f),
            FxParam("Pre Run", 0f, 1f, 0f),
        )),

    Wiggle("wiggle()", FxCategory.Expression, "Goyang acak posisi",
        listOf(FxParam("Frequency", 0f, 12f, 4f), FxParam("Amplitude", 0f, 0.3f, 0.03f))),
    ;

    /** Effects that only make sense on certain layer types. */
    fun appliesTo(type: LayerType): Boolean = when (category) {
        FxCategory.Text -> type == LayerType.Text
        FxCategory.Particles -> type == LayerType.Particles
        else -> type != LayerType.Camera && type != LayerType.Null && (this != Stroke || type == LayerType.Shape)
    }
}

/** One applied effect. Its [params] line up with [FxType.params]. */
data class EffectInstance(val uid: Int, val type: FxType, val enabled: Boolean = true, val params: List<Track>) {
    fun param(i: Int, t: Float, easing: Easing) = params.getOrNull(i)?.valueAt(t, easing) ?: type.params[i].default

    companion object {
        fun of(type: FxType, uid: Int) = EffectInstance(uid, type, params = type.params.map { Track(it.default) })

        /** An instance with some parameters replaced, by index. */
        fun of(type: FxType, uid: Int, vararg overrides: Pair<Int, Track>): EffectInstance {
            val map = overrides.toMap()
            return EffectInstance(uid, type, params = type.params.mapIndexed { i, p -> map[i] ?: Track(p.default) })
        }
    }
}

/** Address of any keyframable value on a layer: a transform property or an effect parameter. */
sealed interface PropKey {
    data class T(val prop: Prop) : PropKey
    data class Fx(val uid: Int, val param: Int) : PropKey
}

data class Layer(
    val id: Int,
    val name: String,
    val type: LayerType,
    val color: Color,
    val start: Float,
    val end: Float,
    val tracks: Map<Prop, Track> = Prop.entries.associateWith { Track(it.default) },
    val easing: Easing = Easing.EaseInOut,
    val text: String = "",
    val shape: ShapeKind = ShapeKind.Circle,
    val modelId: String = "rocket",
    val effects: List<EffectInstance> = emptyList(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val solo: Boolean = false,
    val blend: BlendKind = BlendKind.Normal,
    val parentId: Int? = null,
    /** Video or photo source for footage layers. */
    val footage: Footage? = null,
    /** Seconds into the source where the clip starts (moves when the in point is trimmed). */
    val sourceIn: Float = 0f,
    /** After Effects' 3D switch turned off: the comp camera does not move this layer. */
    val cameraIgnore: Boolean = false,
    /** Camera layers: the layer whose position the point of interest follows. */
    val targetId: Int? = null,
    /** Camera layers: how far behind the target the camera trails, in seconds. */
    val followLag: Float = 0.25f,
    val stabilize: Stabilization? = null,
    val tracked: TrackPath? = null,
    /** Immutable compensation in normalized composition coordinates; preserved by undo/copy. */
    val parentOffset: ParentOffset = ParentOffset(),
    /** A flat content plane in perspective; preserves normal 2D behavior when disabled. */
    val is3D: Boolean = false,
    val cameraSolve: CameraSolve3D? = null,
    val faceSolve: FaceSolve3D? = null,
    val spatialOffset: List<Float> = emptyList(),
) {
    fun track(p: Prop) = tracks[p] ?: Track(p.default)
    fun value(p: Prop, t: Float) = track(p).valueAt(t, easing)
    fun activeAt(t: Float) = visible && t >= start && t <= end
    fun withTrack(p: Prop, track: Track) = copy(tracks = tracks + (p to track))

    fun fx(type: FxType): EffectInstance? = effects.firstOrNull { it.type == type && it.enabled }
    fun has(type: FxType) = fx(type) != null

    /** Time inside the footage for comp time [t]. */
    fun sourceTime(t: Float) = t - start + sourceIn

    fun trackOf(key: PropKey): Track? = when (key) {
        is PropKey.T -> track(key.prop)
        is PropKey.Fx -> effects.firstOrNull { it.uid == key.uid }?.params?.getOrNull(key.param)
    }

    fun withTrackOf(key: PropKey, track: Track): Layer = when (key) {
        is PropKey.T -> withTrack(key.prop, track)
        is PropKey.Fx -> copy(effects = effects.map { e ->
            if (e.uid != key.uid) e else e.copy(params = e.params.mapIndexed { i, p -> if (i == key.param) track else p })
        })
    }

    fun specOf(key: PropKey): FxParam? = when (key) {
        is PropKey.T -> FxParam(key.prop.labelFor(type), key.prop.min, key.prop.max, key.prop.default, key.prop.unit)
        is PropKey.Fx -> effects.firstOrNull { it.uid == key.uid }?.let { e ->
            e.type.params.getOrNull(key.param)?.let { it.copy(label = "${e.type.label} · ${it.label}") }
        }
    }

    /** Every keyframable value that currently has keys, in panel order. */
    val animatedKeys: List<PropKey>
        get() = transformProperties().filter { track(it).animated }.map { PropKey.T(it) } +
            effects.flatMap { e -> e.params.indices.filter { e.params[it].animated }.map { PropKey.Fx(e.uid, it) } }

    val keyTimes: List<Float>
        get() = (transformProperties().map { track(it) } + effects.flatMap { it.params }).flatMap { tr -> tr.keys.map { it.time } }.distinct().sorted()
}

fun keys(vararg pairs: Pair<Float, Float>, easing: Easing? = null) = pairs.map { Keyframe(it.first, it.second, easing) }

fun tracks(
    posX: Track = Track(0f), posY: Track = Track(0f), scale: Track = Track(1f),
    rotation: Track = Track(0f), opacity: Track = Track(1f), posZ: Track = Track(0f),
    orbitX: Track = Track(0f), orbitY: Track = Track(0f),
) = mapOf(
    Prop.PosX to posX, Prop.PosY to posY, Prop.Scale to scale, Prop.Rotation to rotation, Prop.Opacity to opacity,
    Prop.PosZ to posZ, Prop.OrbitX to orbitX, Prop.OrbitY to orbitY,
)

object DemoProjects {
    const val DURATION = 10f
}

enum class TemplateCategory(val label: String) { Intro("Intro"), Typography("Typography"), Typography3D("Typography 3D") }

/**
 * A ready-made composition. [steps] explain how it was built, so the template doubles as a
 * lesson in the editor's tools.
 */
data class Template(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val modelId: String,
    val accent: Color,
    val ratio: String = "16:9",
    val category: TemplateCategory = TemplateCategory.Intro,
    val steps: List<String> = emptyList(),
) {
    fun layers(): List<Layer> = TemplateLibrary.build(this)
}

object Templates {
    val all: List<Template> get() = TemplateLibrary.all
}

