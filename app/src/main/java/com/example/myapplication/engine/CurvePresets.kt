package com.example.myapplication.engine

import kotlin.math.abs

data class CurvePreset(val name: String, val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    fun apply(track: Track, segment: Int? = null): Track {
        val keys = track.keys.toMutableList()
        for (i in 0 until keys.lastIndex) {
            if (segment != null && segment != i) continue
            val a = keys[i]; val b = keys[i+1]
            val slope = (b.value-a.value)/(b.time-a.time).coerceAtLeast(KEY_EPS)
            keys[i] = a.copy(easing=Easing.Bezier, outInfluence=x1, outSpeed=slope*y1/x1.coerceAtLeast(.001f))
            keys[i+1] = b.copy(inInfluence=1-x2, inSpeed=slope*(1-y2)/(1-x2).coerceAtLeast(.001f))
        }
        return track.copy(keys=keys)
    }
}

val FlowCurves = listOf(
    CurvePreset("Linear", .333333f, .333333f, .666667f, .666667f),
    CurvePreset("Easy ease", .333333f, 0f, .666667f, 1f),
    CurvePreset("Smooth", .75f, 0f, .25f, 1f),
    CurvePreset("Swift out", .16f, 1f, .3f, 1f),
    CurvePreset("Slow in", .7f, 0f, .84f, 0f),
    CurvePreset("Cinematic", .76f, 0f, .24f, 1f),
    CurvePreset("Snap", .85f, 0f, .15f, 1f),
    CurvePreset("Gentle", .25f, .1f, .25f, 1f),
    CurvePreset("Overshoot", .34f, 1.56f, .64f, 1f),
    CurvePreset("Anticipate", .36f, 0f, .66f, -.56f),
    CurvePreset("Back in/out", .68f, -.55f, .27f, 1.55f),
    CurvePreset("Accelerate", .55f, .055f, .675f, .19f),
)

fun Track.velocityAt(time: Float, default: Easing): Float {
    if (keys.size < 2) return 0f
    val period = keys.last().time-keys.first().time
    val t = if (loop && period > KEY_EPS && time > keys.last().time)
        keys.first().time+(time-keys.first().time)%period else time
    if (t < keys.first().time || t > keys.last().time) return 0f
    val i = keys.indexOfLast { it.time <= t }.coerceIn(0, keys.lastIndex-1)
    val a=keys[i]; val b=keys[i+1]; val easing=a.easing ?: default
    if (easing == Easing.Hold || b.time-a.time <= KEY_EPS) return 0f
    val d = ((b.time-a.time)*.002f).coerceAtLeast(.0001f)
    val lo=(t-d).coerceAtLeast(a.time); val hi=(t+d).coerceAtMost(b.time)
    return if (abs(hi-lo) < 1e-7f) 0f else (segmentValue(a,b,hi,easing)-segmentValue(a,b,lo,easing))/(hi-lo)
}
