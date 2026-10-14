package com.example.myapplication.engine

import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

private fun quaternion(r: List<Float>): DoubleArray {
    require(r.size == 9 && r.all { it.isFinite() })
    val trace = r[0] + r[4] + r[8]
    val q = when {
        trace > 0 -> {
            val s = sqrt((trace + 1).toDouble()) * 2
            doubleArrayOf((r[7]-r[5])/s, (r[2]-r[6])/s, (r[3]-r[1])/s, s/4)
        }
        r[0] > r[4] && r[0] > r[8] -> {
            val s = sqrt((1+r[0]-r[4]-r[8]).toDouble().coerceAtLeast(0.0)) * 2
            doubleArrayOf(s/4, (r[1]+r[3])/s, (r[2]+r[6])/s, (r[7]-r[5])/s)
        }
        r[4] > r[8] -> {
            val s = sqrt((1+r[4]-r[0]-r[8]).toDouble().coerceAtLeast(0.0)) * 2
            doubleArrayOf((r[1]+r[3])/s, s/4, (r[5]+r[7])/s, (r[2]-r[6])/s)
        }
        else -> {
            val s = sqrt((1+r[8]-r[0]-r[4]).toDouble().coerceAtLeast(0.0)) * 2
            doubleArrayOf((r[2]+r[6])/s, (r[5]+r[7])/s, s/4, (r[3]-r[1])/s)
        }
    }
    val length = sqrt(q.sumOf { it*it }).coerceAtLeast(1e-12)
    return DoubleArray(4) { q[it]/length }
}

// Slerp keeps the interpolated camera rotation orthonormal and takes the shortest arc.
fun interpolateRotation(a: List<Float>, b: List<Float>, fraction: Float): List<Float> {
    val qa = quaternion(a); val qb = quaternion(b)
    var dot = qa.indices.sumOf { qa[it]*qb[it] }
    if (dot < 0) { qb.indices.forEach { qb[it] = -qb[it] }; dot = -dot }
    val u = fraction.coerceIn(0f, 1f).toDouble()
    val q = if (dot > .9995) DoubleArray(4) { qa[it]*(1-u)+qb[it]*u } else {
        val angle = acos(dot.coerceIn(-1.0, 1.0))
        DoubleArray(4) { (qa[it]*sin((1-u)*angle)+qb[it]*sin(u*angle))/sin(angle) }
    }
    val n = sqrt(q.sumOf { it*it }).coerceAtLeast(1e-12)
    val x=q[0]/n; val y=q[1]/n; val z=q[2]/n; val w=q[3]/n
    return listOf(1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w),
        2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w),
        2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)).map { it.toFloat() }
}
