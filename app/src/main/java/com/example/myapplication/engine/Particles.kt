package com.example.myapplication.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// A Trapcode Particular-style emitter. Every particle is a pure function of its birth index and
// the current time, so scrubbing, looping and export always give identical frames.

private val defaults = EffectInstance.of(FxType.Particular, -1)

/** Soft white sprite, tinted per particle, so glows cost one textured quad each. */
private val glowSprite: ImageBitmap by lazy {
    val bmp = ImageBitmap(64, 64)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(64f, 64f)) {
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(32f, 32f), 32f), 32f, Offset(32f, 32f))
    }
    bmp
}

private fun hash(k: Int, salt: Int): Float {
    var h = k * 374761393 + salt * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}

private fun smooth01(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Draws the layer's particles in comp space; [emitterAt] gives the emitter centre at a time. */
internal fun DrawScope.drawParticular(layer: Layer, t: Float, alpha: Float, emitterAt: (Float) -> Offset) {
    val fx = layer.fx(FxType.Particular) ?: defaults
    fun p(i: Int) = fx.param(i, t, layer.easing)
    val rate = p(0); val life = p(1).coerceAtLeast(0.05f)
    if (rate <= 0f) return
    val velocity = p(2); val direction = p(3) * (PI / 180.0).toFloat(); val spread = p(4) * (PI / 180.0).toFloat()
    val gravity = p(5); val turbulence = p(6); val baseSize = p(7); val sizeRandom = p(8)
    val emitterSize = p(9); val emitterType = p(10).toInt(); val glow = p(11); val streak = p(12)
    val opacity = p(13); val colorRandom = p(14); val preRun = p(15) > 0.5f
    val unit = size.minDimension
    val px = unit / 1080f
    val maxLife = life * 1.3f
    val firstBirth = if (preRun) t - maxLife else max(layer.start, t - maxLife)
    var k = floor(firstBirth * rate).toInt()
    val last = floor(t * rate).toInt()
    // Keep very dense emitters affordable on phones.
    val stride = max(1, ((last - k) / 700))
    while (k <= last) {
        val i = k
        k += stride
        val birth = (i + hash(i, 1)) / rate
        if (birth > t || (!preRun && birth < layer.start)) continue
        val lifetime = life * (0.7f + 0.6f * hash(i, 2))
        val age = t - birth
        if (age < 0f || age > lifetime) continue
        val u = age / lifetime
        val origin = emitterAt(birth) + when (emitterType) {
            1 -> Offset((hash(i, 3) - 0.5f) * emitterSize * size.width * 0.5f, 0f)
            2 -> Offset((hash(i, 3) - 0.5f) * emitterSize * size.width * 0.5f, (hash(i, 4) - 0.5f) * emitterSize * size.height * 0.5f)
            else -> Offset((hash(i, 3) - 0.5f) * emitterSize * unit * 0.1f, (hash(i, 4) - 0.5f) * emitterSize * unit * 0.1f)
        }
        val angle = direction + (hash(i, 5) - 0.5f) * spread
        val speed = velocity * (0.5f + hash(i, 6)) * unit
        fun at(a: Float): Offset {
            val ph1 = hash(i, 7) * 6.283f; val ph2 = hash(i, 8) * 6.283f
            val turb = Offset(
                sin(a * 1.7f + ph1) + 0.5f * sin(a * 3.1f + ph2),
                cos(a * 1.3f + ph2) + 0.5f * cos(a * 2.3f + ph1),
            ) * (turbulence * unit * 0.06f)
            return origin + Offset(cos(angle), sin(angle)) * (speed * a) + Offset(0f, 0.5f * gravity * unit * a * a) + turb
        }
        val pos = at(age)
        val fade = smooth01(0f, 0.12f, u) * (1f - smooth01(0.6f, 1f, u))
        val twinkle = 0.7f + 0.3f * sin(t * 3f + i)
        val a = (alpha * opacity * fade * twinkle).coerceIn(0f, 1f)
        if (a < 0.01f) continue
        val radius = baseSize * px * (1f - sizeRandom + sizeRandom * hash(i, 9)) * (0.55f + 0.45f * sin(PI.toFloat() * u))
        val color = lerp(layer.color, Color.White, hash(i, 10) * colorRandom)
        if (glow > 0.01f) {
            val g = radius * 5f
            drawImage(
                glowSprite,
                srcSize = IntSize(64, 64),
                dstOffset = IntOffset((pos.x - g).toInt(), (pos.y - g).toInt()),
                dstSize = IntSize((g * 2).toInt().coerceAtLeast(1), (g * 2).toInt().coerceAtLeast(1)),
                alpha = (a * glow * 0.8f).coerceIn(0f, 1f),
                colorFilter = ColorFilter.tint(color, BlendMode.Modulate),
            )
        }
        if (streak > 0.01f) {
            val tail = at(max(0f, age - streak * 0.25f))
            drawLine(color.copy(alpha = a), tail, pos, strokeWidth = radius * 1.6f, cap = StrokeCap.Round)
        } else {
            drawCircle(color.copy(alpha = a), radius, pos)
        }
    }
}

/** How many particles the emitter has alive, for the panel readout. */
fun particularAlive(layer: Layer, t: Float): Int {
    val fx = layer.fx(FxType.Particular) ?: defaults
    val rate = fx.param(0, t, layer.easing); val life = fx.param(1, t, layer.easing)
    return min(2000, (rate * life).toInt())
}
