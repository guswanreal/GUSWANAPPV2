package com.example.myapplication.engine

import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// GPU effect stack for one layer: After Effects-style effects built from RenderEffect primitives
// (blur, colour matrices, blend) and AGSL runtime shaders for the distort/stylize/generate set.

/** Everything an effect needs to know about the frame it renders into. */
class FxContext(
    val width: Float,
    val height: Float,
    val time: Float,
    /** The layer's anchor in comp pixels, used as the centre for twirl, bulge, ripple and flares. */
    val center: Offset,
    val layerColor: Color,
    val easing: Easing,
) {
    /** The frame's short side: 1080 for 1080p in any aspect ratio. */
    val short: Float get() = minOf(width, height)

    /** Effect sizes are authored against a 1080p comp, then scaled to the actual render size. */
    val px: Float get() = short / 1080f
}

/** Effects drawn by the renderer itself (time sampling, geometry, text, particles) rather than as a RenderEffect. */
val GeometricFx = setOf(
    FxType.Echo, FxType.Wiggle, FxType.Stroke, FxType.MotionBlur, FxType.Shake,
    FxType.TextAnimator, FxType.Typewriter, FxType.TextWave, FxType.Tracking, FxType.Particular,
)

/** Builds the RenderEffect chain for [effects] in stack order, or null when nothing needs the GPU path. */
fun buildRenderEffect(effects: List<EffectInstance>, ctx: FxContext): RenderEffect? {
    var chain: RenderEffect? = null
    for (e in effects) {
        if (!e.enabled || e.type in GeometricFx) continue
        val next = singleEffect(e, ctx) ?: continue
        chain = if (chain == null) next else RenderEffect.createChainEffect(next, chain)
    }
    return chain
}

private fun identity(): RenderEffect = RenderEffect.createOffsetEffect(0f, 0f)

private fun rad(deg: Float) = deg * (PI / 180.0).toFloat()

private fun singleEffect(e: EffectInstance, ctx: FxContext): RenderEffect? {
    fun p(i: Int) = e.param(i, ctx.time, ctx.easing)
    return when (e.type) {
        FxType.GaussianBlur -> {
            val r = p(0) * ctx.px
            if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL)
        }
        FxType.DirectionalBlur -> {
            val a = rad(p(0))
            val len = p(1) * ctx.px
            if (len < 0.5f) null else shader(Shaders.DirectionalBlur) { setFloatUniform("dir", cos(a) * len, sin(a) * len) }
        }
        FxType.RadialBlur -> if (p(0) < 0.005f) null else shader(Shaders.RadialBlur) {
            setFloatUniform("center", ctx.width / 2f, ctx.height / 2f)
            setFloatUniform("amount", p(0))
        }
        FxType.Sharpen -> shader(Shaders.Sharpen) { setFloatUniform("amount", p(0)) }

        FxType.BrightnessContrast -> colorMatrix(brightnessContrast(p(0), p(1)))
        FxType.Exposure -> shader(Shaders.Exposure) {
            setFloatUniform("gain", Math.pow(2.0, p(0).toDouble()).toFloat())
            setFloatUniform("gamma", p(1).coerceAtLeast(0.05f))
        }
        FxType.Levels -> shader(Shaders.Levels) {
            setFloatUniform("inBlack", p(0)); setFloatUniform("inWhite", p(1)); setFloatUniform("gamma", p(2).coerceAtLeast(0.05f))
        }
        FxType.HueSaturation -> colorMatrix(hueSaturation(p(0), p(1)))
        FxType.ColorCycle -> colorMatrix(hueSaturation((ctx.time * p(0) * 360f) % 360f, p(1) - 1f))
        FxType.Tint -> colorMatrix(tint(ctx.layerColor, p(0)))
        FxType.Duotone -> shader(Shaders.Duotone) {
            setFloatUniform("dark", 0.04f, 0.03f, 0.10f)
            setFloatUniform("light", ctx.layerColor.red, ctx.layerColor.green, ctx.layerColor.blue)
            setFloatUniform("amount", p(0))
        }
        FxType.BlackWhite -> colorMatrix(mix(Identity, ColorMatrix().apply { setSaturation(0f) }.array, p(0).coerceIn(0f, 1f)))
        FxType.Threshold -> shader(Shaders.Threshold) { setFloatUniform("level", p(0)) }
        FxType.Invert -> colorMatrix(invert(p(0)))
        FxType.Posterize -> shader(Shaders.Posterize) { setFloatUniform("levels", p(0).coerceAtLeast(2f)) }
        FxType.ColorBalance -> shader(Shaders.ColorBalance) { setFloatUniform("balance",p(0),p(1),p(2)) }
        FxType.ChromaKey -> shader(Shaders.ChromaKey) {
            val key=Color.hsv((p(0)%360f+360f)%360f,1f,1f)
            setFloatUniform("keyColor",key.red,key.green,key.blue)
            setFloatUniform("threshold",p(1)); setFloatUniform("softness",p(2).coerceAtLeast(.001f)); setFloatUniform("despill",p(3))
        }
        FxType.LumaKey -> shader(Shaders.LumaKey) {
            setFloatUniform("threshold",p(0)); setFloatUniform("softness",p(1).coerceAtLeast(.001f)); setFloatUniform("invert",if(p(2)>=.5f) 1f else 0f)
        }

        FxType.WaveWarp -> shader(Shaders.WaveWarp) {
            setFloatUniform("height", p(0) * ctx.px)
            setFloatUniform("width", (p(1) * ctx.px).coerceAtLeast(1f))
            setFloatUniform("phase", ctx.time * p(2) * 2f * PI.toFloat())
        }
        FxType.Ripple -> shader(Shaders.Ripple) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("amp", p(0) * ctx.px)
            setFloatUniform("wavelength", (p(1) * ctx.px).coerceAtLeast(1f))
            setFloatUniform("phase", ctx.time * p(2) * 2f * PI.toFloat())
        }
        FxType.Twirl -> shader(Shaders.Twirl) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("angle", rad(p(0)))
            setFloatUniform("radius", (p(1) * ctx.short * 0.5f).coerceAtLeast(1f))
        }
        FxType.Bulge -> shader(Shaders.Bulge) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("height", p(0))
            setFloatUniform("radius", (p(1) * ctx.short * 0.5f).coerceAtLeast(1f))
        }
        FxType.TurbulentDisplace -> shader(Shaders.Turbulent) {
            setFloatUniform("amount", p(0) * ctx.px)
            setFloatUniform("scale", (p(1) * ctx.px).coerceAtLeast(1f))
            setFloatUniform("evo", ctx.time * p(2))
        }
        FxType.Mirror -> shader(Shaders.Mirror) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("angle", rad(p(0)))
        }
        FxType.Kaleidoscope -> shader(Shaders.Kaleido) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("segments", p(0).coerceAtLeast(2f))
            setFloatUniform("rotation", rad(p(1)))
        }
        FxType.PolarCoordinates -> shader(Shaders.Polar) {
            setFloatUniform("res", ctx.width, ctx.height)
            setFloatUniform("amount", p(0))
        }
        FxType.MotionTile -> shader(Shaders.MotionTile) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("tile", (ctx.width * p(0)).coerceAtLeast(4f), (ctx.height * p(0)).coerceAtLeast(4f))
            setFloatUniform("mirrorEdges", p(1))
        }

        FxType.Glow -> {
            val r = p(0) * ctx.px
            if (r < 0.5f) null else RenderEffect.createBlendModeEffect(identity(), halo(r, p(1)), BlendMode.PLUS)
        }
        FxType.DeepGlow -> {
            // Three blurs an octave apart, summed: a soft core with a long falloff.
            val r = p(0) * ctx.px
            if (r < 0.5f) null else {
                val k = p(1)
                var out = RenderEffect.createBlendModeEffect(identity(), halo(r * 0.25f + 0.5f, k * 0.6f), BlendMode.PLUS)
                out = RenderEffect.createBlendModeEffect(out, halo(r * 0.6f + 0.5f, k * 0.45f), BlendMode.PLUS)
                RenderEffect.createBlendModeEffect(out, halo(r, k * 0.35f), BlendMode.PLUS)
            }
        }
        FxType.LightRays -> shader(Shaders.LightRays) {
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("intensity", p(0))
            setFloatUniform("len", p(1))
        }
        FxType.Shine -> shader(Shaders.Shine) {
            val speed = p(0)
            setFloatUniform("res", ctx.width, ctx.height)
            setFloatUniform("pos", if (speed <= 0.001f) 0.5f else ((ctx.time * speed * 0.5f) % 1f) * 1.8f - 0.4f)
            setFloatUniform("width", p(1))
            setFloatUniform("angle", rad(p(2)))
            setFloatUniform("intensity", p(3))
        }
        FxType.DropShadow -> {
            val d = p(0) * ctx.px * 0.7071f
            val soft = p(1) * ctx.px
            val black = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, p(2), 0f)))
            var shadow = RenderEffect.createColorFilterEffect(black)
            if (soft >= 0.5f) shadow = RenderEffect.createBlurEffect(soft, soft, shadow, Shader.TileMode.DECAL)
            shadow = RenderEffect.createOffsetEffect(d, d, shadow)
            RenderEffect.createBlendModeEffect(shadow, identity(), BlendMode.SRC_OVER)
        }
        FxType.Mosaic -> shader(Shaders.Mosaic) { setFloatUniform("block", (p(0) * ctx.px).coerceAtLeast(1f)) }
        FxType.Halftone -> shader(Shaders.Halftone) { setFloatUniform("cellSize", (p(0) * ctx.px).coerceAtLeast(2f)) }
        FxType.FindEdges -> shader(Shaders.FindEdges) { setFloatUniform("intensity", p(0)) }
        FxType.Emboss -> shader(Shaders.Emboss) { setFloatUniform("strength", p(0)) }
        FxType.ChromaticAberration -> if (p(0) < 0.05f) null else shader(Shaders.RgbSplit) {
            // Breathes slightly over time, like a glitchy lens.
            setFloatUniform("offset", p(0) * ctx.px * (0.75f + 0.25f * sin(ctx.time * 9f)))
        }
        FxType.Glitch -> if (p(0) < 0.01f) null else shader(Shaders.Glitch) {
            setFloatUniform("intensity", p(0))
            setFloatUniform("t", ctx.time)
            setFloatUniform("res", ctx.width, ctx.height)
        }
        FxType.Vhs -> if (p(0) < 0.01f) null else shader(Shaders.Vhs) {
            setFloatUniform("amount", p(0))
            setFloatUniform("t", ctx.time)
            setFloatUniform("unit", ctx.px)
        }
        FxType.Noise -> if (p(0) < 0.01f) null else shader(Shaders.Noise) {
            setFloatUniform("amount", p(0))
            setFloatUniform("seed", floor(ctx.time * 24f) % 97f)
        }
        FxType.Strobe -> {
            val on = sin(ctx.time * p(0) * 2f * PI.toFloat()) > 0.2f
            if (!on || p(1) < 0.01f) null else colorMatrix(brightnessContrast(p(1) * 0.8f, 0f))
        }
        FxType.Vignette -> shader(Shaders.Vignette) {
            setFloatUniform("amount", p(0))
            setFloatUniform("res", ctx.width, ctx.height)
        }

        FxType.FractalNoise -> shader(Shaders.Fractal) {
            setFloatUniform("scale", (p(0) * ctx.px).coerceAtLeast(2f))
            setFloatUniform("contrast", p(1))
            setFloatUniform("evo", ctx.time * p(2))
        }
        FxType.GradientRamp -> shader(Shaders.Ramp) {
            setFloatUniform("res", ctx.width, ctx.height)
            setFloatUniform("angle", rad(p(0)))
            setFloatUniform("mixAmt", p(1))
        }
        FxType.OpticalFlares -> shader(Shaders.OpticalFlares) {
            setFloatUniform("res", ctx.width, ctx.height)
            setFloatUniform("src",ctx.width*.5f*(1+p(1)),ctx.height*.5f*(1+p(2)))
            setFloatUniform("intensity",p(0)); setFloatUniform("radius",p(3)); setFloatUniform("streaks",p(4))
            setFloatUniform("ghosts",p(5)); setFloatUniform("hue",rad(p(6)))
        }
        FxType.LensFlare -> shader(Shaders.Flare) {
            setFloatUniform("res", ctx.width, ctx.height)
            setFloatUniform("src", ctx.center.x + p(1) * ctx.width * 0.5f, ctx.center.y + p(2) * ctx.height * 0.5f)
            setFloatUniform("brightness", p(0))
            setFloatUniform("tint", 1f, 0.82f, 0.6f)
        }
        FxType.LightLeak -> shader(Shaders.LightLeak) {
            setFloatUniform("res", ctx.width, ctx.height)
            setFloatUniform("t", ctx.time * p(1))
            setFloatUniform("intensity", p(0))
        }

        FxType.LinearWipe -> shader(Shaders.LinearWipe) {
            setFloatUniform("completion", p(0))
            setFloatUniform("angle", rad(p(1) - 90f))
            setFloatUniform("feather", p(2))
            setFloatUniform("res", ctx.width, ctx.height)
        }
        FxType.RadialWipe -> shader(Shaders.RadialWipe) {
            setFloatUniform("completion", p(0))
            setFloatUniform("center", ctx.center.x, ctx.center.y)
        }
        FxType.IrisWipe -> shader(Shaders.IrisWipe) {
            setFloatUniform("completion", p(0))
            setFloatUniform("feather", p(1))
            setFloatUniform("center", ctx.center.x, ctx.center.y)
            setFloatUniform("res", ctx.width, ctx.height)
        }
        FxType.VenetianBlinds -> shader(Shaders.Blinds) {
            setFloatUniform("completion", p(0))
            setFloatUniform("band", (ctx.height / p(1).coerceAtLeast(1f)).coerceAtLeast(1f))
        }
        FxType.BlockDissolve -> shader(Shaders.BlockDissolve) {
            setFloatUniform("completion", p(0))
            setFloatUniform("block", (p(1) * ctx.px).coerceAtLeast(1f))
        }

        FxType.Echo, FxType.Wiggle, FxType.Stroke, FxType.MotionBlur, FxType.Shake,
        FxType.TextAnimator, FxType.Typewriter, FxType.TextWave, FxType.Tracking, FxType.Particular -> null
    }
}

/** Blurred, brightened copy of the source used by the glow family. */
private fun halo(radius: Float, gain: Float): RenderEffect {
    val bright = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(gain, 0f, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f, 0f, gain, 0f)))
    return RenderEffect.createColorFilterEffect(bright, RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
}

private fun colorMatrix(values: FloatArray): RenderEffect =
    RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix(values)))

// RuntimeShader compiles its source on construction, so each thread keeps one instance per shader.
// RenderEffect snapshots the uniforms when it is created, so reusing an instance across layers is safe.
private val shaderCache = ThreadLocal.withInitial { HashMap<String, RuntimeShader?>() }

/** A shader that fails to compile on some GPU driver is skipped (and logged) instead of crashing the frame. */
private inline fun shader(src: String, setup: RuntimeShader.() -> Unit): RenderEffect? {
    val cache = shaderCache.get()!!
    val s = if (cache.containsKey(src)) cache[src] else compile(src).also { cache[src] = it }
    s ?: return null
    s.setup()
    return RenderEffect.createRuntimeShaderEffect(s, "content")
}

private fun compile(src: String): RuntimeShader? = try {
    RuntimeShader(src)
} catch (e: IllegalArgumentException) {
    android.util.Log.e("GuswanFx", "AGSL compile failed: ${e.message}")
    null
}

/** Compiles every effect shader once and returns the failures; used as a debug self-check at startup. */
fun shaderCompileErrors(): List<String> = Shaders.all.mapNotNull { (name, src) ->
    try { RuntimeShader(src); null } catch (e: IllegalArgumentException) { "$name: ${e.message}" }
}

// ---------------------------------------------------------------- Colour matrices (unpremultiplied, offsets in 0..255)

private fun brightnessContrast(brightness: Float, contrast: Float): FloatArray {
    val c = 1f + contrast
    val off = 128f * (1f - c) + brightness * 128f
    return floatArrayOf(c, 0f, 0f, 0f, off, 0f, c, 0f, 0f, off, 0f, 0f, c, 0f, off, 0f, 0f, 0f, 1f, 0f)
}

private fun hueSaturation(hueDeg: Float, sat: Float): FloatArray {
    val m = ColorMatrix().apply { setSaturation((1f + sat).coerceAtLeast(0f)) }
    val a = rad(hueDeg)
    val c = cos(a); val s = sin(a)
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    val hue = ColorMatrix(floatArrayOf(
        lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
        lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
        lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
    m.postConcat(hue)
    return m.array
}

private val Identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(20) { a[it] + (b[it] - a[it]) * t }

private fun tint(color: Color, amount: Float): FloatArray {
    val r = color.red; val g = color.green; val b = color.blue
    val toColor = floatArrayOf(
        0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
        0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
        0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    return mix(Identity, toColor, amount.coerceIn(0f, 1f))
}

private fun invert(blendWithOriginal: Float): FloatArray {
    val inv = floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)
    return mix(inv, Identity, blendWithOriginal.coerceIn(0f, 1f))
}

// ---------------------------------------------------------------- AGSL sources

private object Shaders {
    val all: List<Pair<String, String>> by lazy {
        listOf(
            "DirectionalBlur" to DirectionalBlur, "RadialBlur" to RadialBlur, "Sharpen" to Sharpen, "Exposure" to Exposure,
            "Levels" to Levels, "Duotone" to Duotone, "Threshold" to Threshold, "Posterize" to Posterize,
            "WaveWarp" to WaveWarp, "Ripple" to Ripple, "Twirl" to Twirl, "Bulge" to Bulge, "Turbulent" to Turbulent,
            "Mirror" to Mirror, "Kaleido" to Kaleido, "Polar" to Polar, "MotionTile" to MotionTile,
            "LightRays" to LightRays, "Shine" to Shine, "Mosaic" to Mosaic, "Halftone" to Halftone,
            "FindEdges" to FindEdges, "Emboss" to Emboss, "RgbSplit" to RgbSplit, "Glitch" to Glitch, "Vhs" to Vhs,
            "Noise" to Noise, "Vignette" to Vignette, "Fractal" to Fractal, "Ramp" to Ramp, "Flare" to Flare, "OpticalFlares" to OpticalFlares,
            "LightLeak" to LightLeak, "LinearWipe" to LinearWipe, "RadialWipe" to RadialWipe, "IrisWipe" to IrisWipe,
            "Blinds" to Blinds, "BlockDissolve" to BlockDissolve,
            "ColorBalance" to ColorBalance, "ChromaKey" to ChromaKey, "LumaKey" to LumaKey,
        )
    }

    const val DirectionalBlur = """
        uniform shader content; uniform float2 dir;
        half4 main(float2 p) {
            float4 acc = float4(0.0);
            for (int i = 0; i < 16; i++) { acc += content.eval(p + dir * (float(i) / 15.0 - 0.5)); }
            return half4(acc / 16.0);
        }"""

    const val ColorBalance = """
        uniform shader content; uniform float3 balance;
        half4 main(float2 p) {
            float4 c=content.eval(p);
            return half4(clamp(c.rgb+balance*c.a,float3(0.0),float3(c.a)),c.a);
        }"""

    const val ChromaKey = """
        uniform shader content; uniform float3 keyColor; uniform float threshold; uniform float softness; uniform float despill;
        half4 main(float2 p) {
            float4 c=content.eval(p);
            float3 rgb=c.rgb/max(c.a,0.0001);
            float3 chroma=rgb/max(dot(rgb,float3(1.0)),0.0001);
            float3 key=keyColor/max(dot(keyColor,float3(1.0)),0.0001);
            float mask=smoothstep(threshold,threshold+softness,length(chroma-key));
            float spill=max(dot(rgb,keyColor)-dot(rgb,(float3(1.0)-keyColor)*0.5),0.0);
            rgb=max(rgb-keyColor*spill*despill*(1.0-mask),float3(0.0));
            return half4(rgb*c.a*mask,c.a*mask);
        }"""

    const val LumaKey = """
        uniform shader content; uniform float threshold; uniform float softness; uniform float invert;
        half4 main(float2 p) {
            float4 c=content.eval(p);
            float luminance=dot(c.rgb/max(c.a,0.0001),float3(0.2126,0.7152,0.0722));
            float mask=smoothstep(threshold-softness,threshold+softness,luminance);
            return half4(c*mix(mask,1.0-mask,invert));
        }"""

    const val RadialBlur = """
        uniform shader content; uniform float2 center; uniform float amount;
        half4 main(float2 p) {
            float2 d = p - center;
            float4 acc = float4(0.0);
            for (int i = 0; i < 16; i++) { acc += content.eval(center + d * (1.0 - amount * 0.3 * float(i) / 15.0)); }
            return half4(acc / 16.0);
        }"""

    const val Sharpen = """
        uniform shader content; uniform float amount;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            float4 n = content.eval(p + float2(1.0, 0.0)) + content.eval(p - float2(1.0, 0.0))
                     + content.eval(p + float2(0.0, 1.0)) + content.eval(p - float2(0.0, 1.0));
            float3 rgb = c.rgb + (c.rgb * 4.0 - n.rgb) * amount * 0.5;
            return half4(clamp(rgb, float3(0.0), float3(c.a)), c.a);
        }"""

    const val Exposure = """
        uniform shader content; uniform float gain; uniform float gamma;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float3 rgb = pow(clamp(c.rgb / c.a * gain, 0.0, 1.0), float3(1.0 / gamma));
            return half4(rgb * c.a, c.a);
        }"""

    const val Levels = """
        uniform shader content; uniform float inBlack; uniform float inWhite; uniform float gamma;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float3 rgb = clamp((c.rgb / c.a - inBlack) / max(inWhite - inBlack, 0.001), 0.0, 1.0);
            rgb = pow(rgb, float3(1.0 / gamma));
            return half4(rgb * c.a, c.a);
        }"""

    const val Duotone = """
        uniform shader content; uniform float3 dark; uniform float3 light; uniform float amount;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float3 rgb = c.rgb / c.a;
            float l = dot(rgb, float3(0.299, 0.587, 0.114));
            return half4(mix(rgb, mix(dark, light, l), amount) * c.a, c.a);
        }"""

    const val Threshold = """
        uniform shader content; uniform float level;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float v = step(level, dot(c.rgb / c.a, float3(0.299, 0.587, 0.114)));
            return half4(float3(v) * c.a, c.a);
        }"""

    const val Posterize = """
        uniform shader content; uniform float levels;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float n = levels - 1.0;
            float3 rgb = floor(c.rgb / c.a * n + 0.5) / n;
            return half4(rgb * c.a, c.a);
        }"""

    const val WaveWarp = """
        uniform shader content; uniform float height; uniform float width; uniform float phase;
        half4 main(float2 p) {
            float off = sin(p.x / width * 6.2831853 + phase) * height;
            return content.eval(float2(p.x, p.y + off));
        }"""

    const val Ripple = """
        uniform shader content; uniform float2 center; uniform float amp; uniform float wavelength; uniform float phase;
        half4 main(float2 p) {
            float2 d = p - center;
            float r = length(d);
            if (r < 0.5) { return content.eval(p); }
            float off = sin(r / wavelength * 6.2831853 - phase) * amp;
            return content.eval(p + d / r * off);
        }"""

    const val Twirl = """
        uniform shader content; uniform float2 center; uniform float angle; uniform float radius;
        half4 main(float2 p) {
            float2 d = p - center;
            float r = length(d);
            if (r < radius) {
                float k = 1.0 - r / radius;
                float a = angle * k * k;
                float s = sin(a); float c = cos(a);
                d = float2(d.x * c - d.y * s, d.x * s + d.y * c);
            }
            return content.eval(center + d);
        }"""

    const val Bulge = """
        uniform shader content; uniform float2 center; uniform float height; uniform float radius;
        half4 main(float2 p) {
            float2 d = p - center;
            float r = length(d);
            if (r < radius) {
                float k = r / radius;
                d *= 1.0 - height * 0.6 * (1.0 - k * k);
            }
            return content.eval(center + d);
        }"""

    const val Turbulent = """
        uniform shader content; uniform float amount; uniform float scale; uniform float evo;
        float hash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 q) {
            float2 i = floor(q); float2 f = fract(q);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                       mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        half4 main(float2 p) {
            float2 q = p / scale;
            float nx = noise(q + float2(evo, 0.0)) + 0.5 * noise(q * 2.0 + float2(0.0, evo));
            float ny = noise(q + float2(5.2, evo * 1.3)) + 0.5 * noise(q * 2.0 + float2(evo, 3.1));
            return content.eval(p + (float2(nx, ny) / 1.5 * 2.0 - 1.0) * amount);
        }"""

    const val Mirror = """
        uniform shader content; uniform float2 center; uniform float angle;
        half4 main(float2 p) {
            float2 n = float2(cos(angle), sin(angle));
            float d = dot(p - center, n);
            float2 q = p;
            if (d > 0.0) { q = p - 2.0 * d * n; }
            return content.eval(q);
        }"""

    const val Kaleido = """
        uniform shader content; uniform float2 center; uniform float segments; uniform float rotation;
        half4 main(float2 p) {
            float2 d = p - center;
            float r = length(d);
            float seg = 6.2831853 / segments;
            float a = mod(atan(d.y, d.x) - rotation, seg);
            if (a > seg * 0.5) { a = seg - a; }
            a += rotation;
            return content.eval(center + float2(cos(a), sin(a)) * r);
        }"""

    const val Polar = """
        uniform shader content; uniform float2 res; uniform float amount;
        half4 main(float2 p) {
            float2 d = p - res * 0.5;
            float r = length(d) / (min(res.x, res.y) * 0.5);
            float a = atan(d.x, -d.y) / 6.2831853 + 0.5;
            float2 src = float2(a * res.x, r * res.y);
            return content.eval(mix(p, src, amount));
        }"""

    const val MotionTile = """
        uniform shader content; uniform float2 center; uniform float2 tile; uniform float mirrorEdges;
        half4 main(float2 p) {
            float2 q = (p - center) / tile + 0.5;
            float2 cell = floor(q);
            float2 f = q - cell;
            if (mirrorEdges > 0.5) {
                if (mod(cell.x, 2.0) > 0.5) { f.x = 1.0 - f.x; }
                if (mod(cell.y, 2.0) > 0.5) { f.y = 1.0 - f.y; }
            }
            return content.eval(center + (f - 0.5) * tile);
        }"""

    const val LightRays = """
        uniform shader content; uniform float2 center; uniform float intensity; uniform float len;
        half4 main(float2 p) {
            float4 base = content.eval(p);
            float2 d = (center - p) * len / 24.0;
            float4 acc = float4(0.0);
            float2 q = p;
            float w = 1.0;
            for (int i = 0; i < 24; i++) { acc += content.eval(q) * w; q += d; w *= 0.93; }
            return half4(min(base + acc / 10.0 * intensity, float4(1.0)));
        }"""

    const val Shine = """
        uniform shader content; uniform float2 res; uniform float pos; uniform float width; uniform float angle; uniform float intensity;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float2 n = float2(cos(angle), sin(angle));
            float extent = abs(n.x) * res.x + abs(n.y) * res.y;
            float d = dot(p - res * 0.5, n) / extent + 0.5;
            float band = 1.0 - smoothstep(0.0, width, abs(d - pos));
            float3 rgb = c.rgb + float3(band * intensity) * c.a;
            return half4(min(rgb, float3(c.a)), c.a);
        }"""

    const val Mosaic = """
        uniform shader content; uniform float block;
        half4 main(float2 p) { return content.eval((floor(p / block) + 0.5) * block); }"""

    const val Halftone = """
        uniform shader content; uniform float cellSize;
        half4 main(float2 p) {
            float2 cell = (floor(p / cellSize) + 0.5) * cellSize;
            float4 c = content.eval(cell);
            if (c.a <= 0.0) { return half4(0.0); }
            float l = dot(c.rgb / c.a, float3(0.299, 0.587, 0.114));
            float r = sqrt(l) * cellSize * 0.62;
            float m = 1.0 - smoothstep(r - 0.8, r + 0.8, length(p - cell));
            return half4(c.rgb / c.a * m * c.a, m * c.a);
        }"""

    const val FindEdges = """
        uniform shader content; uniform float intensity;
        float lum(float2 q) { float4 c = content.eval(q); return dot(c.rgb, float3(0.299, 0.587, 0.114)) + c.a * 0.25; }
        half4 main(float2 p) {
            float tl = lum(p + float2(-1.0, -1.0)); float tm = lum(p + float2(0.0, -1.0)); float tr = lum(p + float2(1.0, -1.0));
            float ml = lum(p + float2(-1.0, 0.0)); float mr = lum(p + float2(1.0, 0.0));
            float bl = lum(p + float2(-1.0, 1.0)); float bm = lum(p + float2(0.0, 1.0)); float br = lum(p + float2(1.0, 1.0));
            float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
            float gy = -tl - 2.0 * tm - tr + bl + 2.0 * bm + br;
            float e = clamp(length(float2(gx, gy)) * intensity, 0.0, 1.0);
            return half4(e, e, e, e);
        }"""

    const val Emboss = """
        uniform shader content; uniform float strength;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            float4 a = content.eval(p - float2(1.5, 1.5));
            float4 b = content.eval(p + float2(1.5, 1.5));
            float d = (dot(a.rgb, float3(0.333)) - dot(b.rgb, float3(0.333))) * strength;
            float v = clamp(0.5 + d, 0.0, 1.0);
            return half4(float3(v) * c.a, c.a);
        }"""

    const val RgbSplit = """
        uniform shader content; uniform float offset;
        half4 main(float2 p) {
            float4 r = content.eval(p - float2(offset, 0.0));
            float4 g = content.eval(p);
            float4 b = content.eval(p + float2(offset, 0.0));
            return half4(r.r, g.g, b.b, max(max(r.a, g.a), b.a));
        }"""

    const val Glitch = """
        uniform shader content; uniform float intensity; uniform float t; uniform float2 res;
        float hash(float n) { return fract(sin(n) * 43758.5453); }
        half4 main(float2 p) {
            float st = floor(t * 12.0);
            float row = floor(p.y / (res.y / 18.0));
            float h = hash(row * 13.1 + st * 7.7);
            float shift = h > 1.0 - intensity * 0.6 ? (hash(row + st * 3.3) - 0.5) * res.x * 0.15 * intensity : 0.0;
            float2 q = float2(p.x + shift, p.y);
            float o = intensity * res.x * 0.008;
            float4 r = content.eval(q - float2(o, 0.0));
            float4 g = content.eval(q);
            float4 b = content.eval(q + float2(o, 0.0));
            return half4(r.r, g.g, b.b, max(max(r.a, g.a), b.a));
        }"""

    const val Vhs = """
        uniform shader content; uniform float amount; uniform float t; uniform float unit;
        float hash(float n) { return fract(sin(n) * 43758.5453); }
        half4 main(float2 p) {
            float band = floor(p.y / (24.0 * unit));
            float tear = step(0.93, hash(band + floor(t * 12.0))) * (hash(band * 7.0 + t) - 0.5) * 30.0 * unit;
            float wobble = sin(p.y * 0.02 / unit + t * 3.0) * amount * 4.0 * unit;
            float2 q = float2(p.x + (tear + wobble) * amount, p.y);
            float off = amount * 3.0 * unit;
            float4 r = content.eval(q + float2(off, 0.0));
            float4 g = content.eval(q);
            float4 b = content.eval(q - float2(off, 0.0));
            float4 c = float4(r.r, g.g, b.b, max(max(r.a, g.a), b.a));
            float scan = 0.82 + 0.18 * sin(p.y / unit * 3.14159);
            return half4(c.rgb * mix(1.0, scan, amount), c.a);
        }"""

    const val Noise = """
        uniform shader content; uniform float amount; uniform float seed;
        float hash(float2 q) { return fract(sin(dot(q, float2(12.9898, 78.233)) + seed) * 43758.5453); }
        half4 main(float2 p) {
            float4 c = content.eval(p);
            float n = (hash(floor(p)) - 0.5) * amount;
            return half4(clamp(c.rgb + n * c.a, float3(0.0), float3(c.a)), c.a);
        }"""

    const val Vignette = """
        uniform shader content; uniform float amount; uniform float2 res;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            float2 uv = (p / res - 0.5) * float2(res.x / res.y, 1.0);
            float v = 1.0 - smoothstep(0.3, 0.95, length(uv)) * amount;
            return half4(c.rgb * v, c.a);
        }"""

    const val Fractal = """
        uniform shader content; uniform float scale; uniform float contrast; uniform float evo;
        float hash(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 q) {
            float2 i = floor(q); float2 f = fract(q);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                       mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float2 q = p / scale + float2(evo, evo * 0.7);
            float v = 0.0; float a = 0.5;
            for (int i = 0; i < 5; i++) { v += a * noise(q); q = q * 2.03 + float2(1.7, 9.2); a *= 0.5; }
            v = clamp((v - 0.5) * contrast + 0.5, 0.0, 1.0);
            return half4(c.rgb / c.a * v * c.a, c.a);
        }"""

    const val Ramp = """
        uniform shader content; uniform float2 res; uniform float angle; uniform float mixAmt;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            if (c.a <= 0.0) { return half4(0.0); }
            float2 n = float2(cos(angle), sin(angle));
            float extent = abs(n.x) * res.x + abs(n.y) * res.y;
            float d = clamp(dot(p - res * 0.5, n) / extent + 0.5, 0.0, 1.0);
            float3 rgb = c.rgb / c.a;
            float3 ramp = rgb * mix(1.15, 0.12, d);
            return half4(clamp(mix(rgb, ramp, mixAmt), 0.0, 1.0) * c.a, c.a);
        }"""

    const val OpticalFlares = """
        uniform shader content;
        uniform float2 res; uniform float2 src;
        uniform float intensity; uniform float radius; uniform float streaks; uniform float ghosts; uniform float hue;
        half4 main(float2 p) {
            float4 base = content.eval(p);
            float unit = max(min(res.x,res.y),1.0);
            float2 q = (p-src)/unit/max(radius,0.05);
            float d = length(q);
            float core = exp(-d*d/0.00022)*2.5 + exp(-d*22.0)*0.24;
            float halo = exp(-pow((d-0.12)/0.009,2.0))*0.10;
            float horizontal = exp(-abs(q.y)*550.0)*exp(-abs(q.x)*4.0)*streaks;
            float angle = atan(q.y,q.x+0.00001);
            float spikes = pow(abs(cos(angle*4.0)),24.0)*exp(-d*35.0)*0.13;
            float3 tint = 0.65+0.35*cos(hue+float3(0.0,2.094,4.189));
            float3 light = tint*(core+halo+horizontal+spikes);
            float2 axis=res*0.5-src;
            for(int i=1;i<=6;i++) {
                float fi=float(i);
                float2 center=src+axis*(0.40+fi*0.34);
                float2 g=(p-center)/unit;
                float r=(0.018+0.007*fi)*radius;
                float disc=(1.0-smoothstep(r*0.65,r,length(g)))*0.04;
                float rim=exp(-pow((length(g)-r)/max(r*0.08,0.001),2.0))*0.015;
                float3 spectral=0.5+0.5*cos(fi*1.1+float3(0.0,2.1,4.2));
                light+=(disc+rim)*spectral*ghosts;
            }
            light=1.0-exp(-max(light,0.0)*intensity);
            float alpha=max(light.r,max(light.g,light.b));
            return half4(base.rgb+(1.0-base.rgb)*light,base.a+(1.0-base.a)*alpha);
        }
    """

    const val Flare = """
        uniform shader content; uniform float2 res; uniform float2 src; uniform float brightness; uniform float3 tint;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            float2 uv = (p - src) / res.y;
            float d = length(uv);
            float core = 0.02 / (d + 0.02);
            float ring = (1.0 - smoothstep(0.0, 0.02, abs(d - 0.18))) * 0.25;
            float streak = 0.012 / (abs(uv.y) + 0.012) * (1.0 - smoothstep(0.0, 0.6, abs(uv.x))) * 0.35;
            float2 axis = res * 0.5 - src;
            float g = 0.0;
            for (int i = 1; i < 5; i++) {
                float2 gp = src + axis * (float(i) * 0.55);
                float gd = length((p - gp) / res.y);
                g += (1.0 - smoothstep(0.0, 0.06 / float(i) + 0.02, gd)) * 0.18;
            }
            float3 light = (tint * (core + ring + streak) + float3(0.9, 0.95, 1.0) * g) * brightness;
            return half4(min(c.rgb + light, float3(1.0)), min(c.a + max(max(light.r, light.g), light.b), 1.0));
        }"""

    const val LightLeak = """
        uniform shader content; uniform float2 res; uniform float t; uniform float intensity;
        half4 main(float2 p) {
            float4 c = content.eval(p);
            float2 uv = p / res;
            float3 a = float3(1.0, 0.45, 0.15) * (1.0 - smoothstep(0.0, 0.7, length(uv - float2(0.15 + 0.2 * sin(t * 0.7), 0.2 + 0.15 * cos(t * 0.5)))));
            float3 b = float3(1.0, 0.75, 0.3) * (1.0 - smoothstep(0.0, 0.6, length(uv - float2(0.85 + 0.1 * cos(t * 0.6), 0.8 + 0.12 * sin(t * 0.9)))));
            float3 m = float3(0.9, 0.2, 0.35) * (1.0 - smoothstep(0.0, 0.5, length(uv - float2(0.6 + 0.25 * sin(t * 0.4), 0.15 + 0.1 * sin(t * 1.1)))));
            float3 light = (a + b + m) * intensity;
            return half4(min(c.rgb + light * 0.8, float3(1.0)), min(c.a + max(max(light.r, light.g), light.b) * 0.6, 1.0));
        }"""

    const val LinearWipe = """
        uniform shader content; uniform float completion; uniform float angle; uniform float feather; uniform float2 res;
        half4 main(float2 p) {
            float2 dir = float2(cos(angle), sin(angle));
            float extent = abs(dir.x) * res.x * 0.5 + abs(dir.y) * res.y * 0.5;
            float d = dot(p - res * 0.5, dir) / extent;
            float f = max(feather * 2.0, 0.001);
            float edge = mix(-1.0 - f, 1.0 + f, completion);
            return content.eval(p) * smoothstep(edge - f, edge + f, d);
        }"""

    const val RadialWipe = """
        uniform shader content; uniform float completion; uniform float2 center;
        half4 main(float2 p) {
            float2 d = p - center;
            float a = atan(d.x, -d.y);
            float u = (a < 0.0 ? a + 6.2831853 : a) / 6.2831853;
            return content.eval(p) * smoothstep(completion - 0.004, completion + 0.004, u);
        }"""

    const val IrisWipe = """
        uniform shader content; uniform float completion; uniform float feather; uniform float2 center; uniform float2 res;
        half4 main(float2 p) {
            float maxR = length(res) * 0.75;
            float r = (1.0 - completion) * maxR;
            float m = 1.0 - smoothstep(r - feather * maxR - 0.5, r + 0.5, length(p - center));
            return content.eval(p) * m;
        }"""

    const val Blinds = """
        uniform shader content; uniform float completion; uniform float band;
        half4 main(float2 p) {
            float f = fract(p.y / band);
            return content.eval(p) * step(completion, f);
        }"""

    const val BlockDissolve = """
        uniform shader content; uniform float completion; uniform float block;
        float hash(float2 q) { return fract(sin(dot(q, float2(12.9898, 78.233))) * 43758.5453); }
        half4 main(float2 p) {
            float h = hash(floor(p / block));
            return content.eval(p) * step(completion, h * 0.999 + 0.0005);
        }"""
}

