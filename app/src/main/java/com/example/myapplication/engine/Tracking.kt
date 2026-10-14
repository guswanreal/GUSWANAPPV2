package com.example.myapplication.engine

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Footage analysis for the Tracker: point tracking (Track Motion), global camera motion
// (Warp Stabilizer, Track Camera). Runs on the proxy frames at about 320 px.
//
// Motion is stored as complex similarities z' = m·z + c, with z in units of frame width and
// the origin at the frame centre: m carries rotation and scale, c the translation.

/** Scene motion from frame 0 to each frame i: content at z in frame 0 appears at m·z + c in frame i. */
class GlobalMotion(val mRe: FloatArray, val mIm: FloatArray, val cRe: FloatArray, val cIm: FloatArray) {
    val frames: Int get() = mRe.size
}

/** Per-frame correction that moves frame content onto the smoothed camera path, plus the crop that hides the edges. */
class Stabilization(
    val smoothness: Float,
    val mRe: FloatArray, val mIm: FloatArray, val cRe: FloatArray, val cIm: FloatArray,
    val crop: Float,
) {
    fun index(i: Int) = i.coerceIn(0, mRe.size - 1)
}

/** A tracked point per source frame in normalised footage coordinates (0–1); NaN where it was lost. */
class TrackPath(val x: FloatArray, val y: FloatArray) {
    val trackedCount: Int get() = x.count { !it.isNaN() }

    /** Position at frame [i], holding the nearest tracked frame when [i] itself was lost. */
    fun at(i: Int): Pair<Float, Float>? {
        if (x.isEmpty()) return null
        val c = i.coerceIn(0, x.size - 1)
        for (d in 0 until x.size) {
            if (c - d >= 0 && !x[c - d].isNaN()) return x[c - d] to y[c - d]
            if (c + d < x.size && !x[c + d].isNaN()) return x[c + d] to y[c + d]
        }
        return null
    }
}

/** Greyscale frame, values 0–1. */
internal class Gray(val w: Int, val h: Int, val v: FloatArray) {
    fun bilinear(x: Float, y: Float): Float {
        val cx = x.coerceIn(0f, w - 1.001f); val cy = y.coerceIn(0f, h - 1.001f)
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val fx = cx - x0; val fy = cy - y0
        val i = y0 * w + x0
        val top = v[i] + (v[i + 1] - v[i]) * fx
        val bottom = v[i + w] + (v[i + w + 1] - v[i + w]) * fx
        return top + (bottom - top) * fy
    }

    fun half(): Gray {
        val hw = w / 2; val hh = h / 2
        val out = FloatArray(hw * hh)
        for (y in 0 until hh) for (x in 0 until hw) {
            val i = (y * 2) * w + x * 2
            out[y * hw + x] = (v[i] + v[i + 1] + v[i + w] + v[i + w + 1]) * 0.25f
        }
        return Gray(hw, hh, out)
    }
}

internal fun loadGray(f: Footage, index: Int): Gray? {
    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
    val bmp = BitmapFactory.decodeFile(f.proxyFile(index).path, opts) ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val out = FloatArray(px.size) { i ->
        val c = px[i]
        (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
    }
    val g = Gray(bmp.width, bmp.height, out)
    bmp.recycle()
    return g
}

private fun extract(img: Gray, x: Float, y: Float, r: Int): FloatArray {
    val side = 2 * r + 1
    return FloatArray(side * side) { k -> img.bilinear(x + (k % side - r), y + (k / side - r)) }
}

/** SSD search for [tmpl] around (cx, cy) within ±[search]; returns subpixel x, y and mean squared error. */
private fun matchTemplate(img: Gray, tmpl: FloatArray, r: Int, cx: Float, cy: Float, search: Int): FloatArray? {
    val side = 2 * r + 1
    val icx = cx.roundToInt(); val icy = cy.roundToInt()
    val span = 2 * search + 1
    val scores = FloatArray(span * span) { Float.MAX_VALUE }
    var best = Float.MAX_VALUE; var bx = 0; var by = 0
    for (dy in -search..search) for (dx in -search..search) {
        val x0 = icx + dx - r; val y0 = icy + dy - r
        if (x0 < 0 || y0 < 0 || x0 + side > img.w || y0 + side > img.h) continue
        var s = 0f; var k = 0
        for (j in 0 until side) {
            val row = (y0 + j) * img.w + x0
            for (i in 0 until side) { val d = img.v[row + i] - tmpl[k++]; s += d * d }
        }
        scores[(dy + search) * span + dx + search] = s
        if (s < best) { best = s; bx = dx; by = dy }
    }
    if (best == Float.MAX_VALUE) return null
    fun sc(dx: Int, dy: Int) = if (abs(dx) > search || abs(dy) > search) Float.MAX_VALUE else scores[(dy + search) * span + dx + search]
    val ox = parabola(sc(bx - 1, by), best, sc(bx + 1, by))
    val oy = parabola(sc(bx, by - 1), best, sc(bx, by + 1))
    return floatArrayOf(icx + bx + ox, icy + by + oy, best / (side * side))
}

private fun parabola(a: Float, b: Float, c: Float): Float {
    if (a == Float.MAX_VALUE || c == Float.MAX_VALUE) return 0f
    val den = a - 2 * b + c
    return if (den <= 1e-9f) 0f else (0.5f * (a - c) / den).coerceIn(-0.5f, 0.5f)
}

/** Coarse search at half resolution, refined at full resolution. */
private fun pyramidMatch(a0: Gray, a1: Gray, b0: Gray, b1: Gray, x: Float, y: Float, gx: Float, gy: Float, coarseSearch: Int): FloatArray? {
    val m1 = matchTemplate(b1, extract(a1, x / 2, y / 2, 4), 4, gx / 2, gy / 2, coarseSearch) ?: return null
    return matchTemplate(b0, extract(a0, x, y, 7), 7, m1[0] * 2, m1[1] * 2, 2)
}

/** Shi-Tomasi corners: the strongest one per grid cell, skipping flat areas that can't be tracked. */
private fun selectFeatures(g: Gray, cols: Int, rows: Int): List<FloatArray> {
    val margin = 16
    val out = ArrayList<FloatArray>()
    val cw = (g.w - 2 * margin) / cols; val ch = (g.h - 2 * margin) / rows
    if (cw < 4 || ch < 4) return out
    for (r in 0 until rows) for (c in 0 until cols) {
        var best = 0f; var bx = 0; var by = 0
        var y = margin + r * ch + 2
        while (y < margin + (r + 1) * ch - 2) {
            var x = margin + c * cw + 2
            while (x < margin + (c + 1) * cw - 2) {
                var sxx = 0f; var sxy = 0f; var syy = 0f
                for (j in -2..2) for (i in -2..2) {
                    val p = (y + j) * g.w + x + i
                    val gx = (g.v[p + 1] - g.v[p - 1]) * 0.5f
                    val gy = (g.v[p + g.w] - g.v[p - g.w]) * 0.5f
                    sxx += gx * gx; sxy += gx * gy; syy += gy * gy
                }
                val half = (sxx + syy) / 2f
                val score = half - sqrt(((sxx - syy) / 2f).let { it * it } + sxy * sxy)
                if (score > best) { best = score; bx = x; by = y }
                x += 3
            }
            y += 3
        }
        if (best > 0.0015f) out += floatArrayOf(bx.toFloat(), by.toFloat())
    }
    return out
}

private class Sim(val mRe: Float, val mIm: Float, val cRe: Float, val cIm: Float) {
    companion object { val Identity = Sim(1f, 0f, 0f, 0f) }
}

/** Least-squares similarity mapping points p → q (complex form), ignoring entries marked false in [use]. */
private fun lsSimilarity(px: FloatArray, py: FloatArray, qx: FloatArray, qy: FloatArray, use: BooleanArray): Sim {
    var n = 0; var pcx = 0f; var pcy = 0f; var qcx = 0f; var qcy = 0f
    for (i in px.indices) if (use[i]) { n++; pcx += px[i]; pcy += py[i]; qcx += qx[i]; qcy += qy[i] }
    if (n < 2) return Sim.Identity
    pcx /= n; pcy /= n; qcx /= n; qcy /= n
    var a = 0f; var b = 0f; var den = 0f
    for (i in px.indices) if (use[i]) {
        val x = px[i] - pcx; val y = py[i] - pcy; val u = qx[i] - qcx; val v = qy[i] - qcy
        a += x * u + y * v; b += x * v - y * u; den += x * x + y * y
    }
    if (den < 1e-9f) return Sim(1f, 0f, qcx - pcx, qcy - pcy)
    a /= den; b /= den
    return Sim(a, b, qcx - (a * pcx - b * pcy), qcy - (b * pcx + a * pcy))
}

/** Robust fit: median translation to drop outliers, then least squares, then one residual pass. */
private fun fitSimilarity(px: FloatArray, py: FloatArray, qx: FloatArray, qy: FloatArray): Sim {
    if (px.size < 3) return Sim.Identity
    val dxs = FloatArray(px.size) { qx[it] - px[it] }.sorted()
    val dys = FloatArray(px.size) { qy[it] - py[it] }.sorted()
    val mdx = dxs[dxs.size / 2]; val mdy = dys[dys.size / 2]
    val use = BooleanArray(px.size) { hypot(qx[it] - px[it] - mdx, qy[it] - py[it] - mdy) < 0.012f }
    var s = lsSimilarity(px, py, qx, qy, use)
    for (i in px.indices) {
        val ex = s.mRe * px[i] - s.mIm * py[i] + s.cRe - qx[i]
        val ey = s.mIm * px[i] + s.mRe * py[i] + s.cIm - qy[i]
        use[i] = hypot(ex, ey) < 0.006f
    }
    if (use.count { it } >= 3) s = lsSimilarity(px, py, qx, qy, use)
    // Reject implausible jumps between neighbouring frames.
    val scale = hypot(s.mRe, s.mIm)
    return if (scale !in 0.8f..1.25f) Sim.Identity else s
}

object MotionAnalyzer {
    private val cache = HashMap<String, GlobalMotion>()

    fun cached(f: Footage): GlobalMotion? = synchronized(cache) { cache[f.id] }

    /** Scene motion through the whole clip; the result is cached per footage. */
    suspend fun globalMotion(f: Footage, onProgress: (Float) -> Unit): GlobalMotion = withContext(Dispatchers.Default) {
        cached(f)?.let { return@withContext it }
        val n = f.frameCount
        val mRe = FloatArray(n) { 1f }; val mIm = FloatArray(n); val cRe = FloatArray(n); val cIm = FloatArray(n)
        var a0 = loadGray(f, 0) ?: throw ImportException("Frame video tidak bisa dibaca.")
        var a1 = a0.half()
        var am = 1f; var amI = 0f; var ac = 0f; var acI = 0f
        for (i in 1 until n) {
            currentCoroutineContext().ensureActive()
            val b0 = loadGray(f, i) ?: break
            val b1 = b0.half()
            val step = pairMotion(a0, a1, b0, b1)
            // Compose: this step after everything so far.
            val nm = step.mRe * am - step.mIm * amI; val nmI = step.mRe * amI + step.mIm * am
            val nc = step.mRe * ac - step.mIm * acI + step.cRe; val ncI = step.mRe * acI + step.mIm * ac + step.cIm
            am = nm; amI = nmI; ac = nc; acI = ncI
            mRe[i] = am; mIm[i] = amI; cRe[i] = ac; cIm[i] = acI
            a0 = b0; a1 = b1
            onProgress(i.toFloat() / (n - 1))
        }
        GlobalMotion(mRe, mIm, cRe, cIm).also { synchronized(cache) { cache[f.id] = it } }
    }

    private fun pairMotion(a0: Gray, a1: Gray, b0: Gray, b1: Gray): Sim {
        val feats = selectFeatures(a0, 8, 5)
        val px = ArrayList<Float>(); val py = ArrayList<Float>(); val qx = ArrayList<Float>(); val qy = ArrayList<Float>()
        val w = a0.w.toFloat(); val cx = a0.w / 2f; val cy = a0.h / 2f
        for (p in feats) {
            val m = pyramidMatch(a0, a1, b0, b1, p[0], p[1], p[0], p[1], coarseSearch = 10) ?: continue
            if (m[2] > 0.02f) continue
            px += (p[0] - cx) / w; py += (p[1] - cy) / w
            qx += (m[0] - cx) / w; qy += (m[1] - cy) / w
        }
        return fitSimilarity(px.toFloatArray(), py.toFloatArray(), qx.toFloatArray(), qy.toFloatArray())
    }

    /** Track Motion: follows one point forwards and backwards from [startIndex]. */
    suspend fun trackPoint(f: Footage, startIndex: Int, u: Float, v: Float, onProgress: (Float) -> Unit): TrackPath = withContext(Dispatchers.Default) {
        val n = f.frameCount
        val xs = FloatArray(n) { Float.NaN }; val ys = FloatArray(n) { Float.NaN }
        val start = startIndex.coerceIn(0, n - 1)
        val g0 = loadGray(f, start) ?: throw ImportException("Frame video tidak bisa dibaca.")
        xs[start] = u; ys[start] = v
        var done = 0
        for (dir in intArrayOf(1, -1)) {
            var x = u * g0.w; var y = v * g0.h
            var vx = 0f; var vy = 0f
            val t0 = extract(g0, x, y, 7)
            val t1 = extract(g0.half(), x / 2, y / 2, 4)
            var i = start + dir
            while (i in 0 until n) {
                currentCoroutineContext().ensureActive()
                val b0 = loadGray(f, i) ?: break
                val b1 = b0.half()
                val m1 = matchTemplate(b1, t1, 4, (x + vx) / 2, (y + vy) / 2, 12) ?: break
                val m0 = matchTemplate(b0, t0, 7, m1[0] * 2, m1[1] * 2, 3) ?: break
                if (m0[2] > 0.018f) break
                vx = 0.6f * (m0[0] - x) + 0.4f * vx; vy = 0.6f * (m0[1] - y) + 0.4f * vy
                x = m0[0]; y = m0[1]
                if (x < 6 || y < 6 || x > b0.w - 7 || y > b0.h - 7) break
                xs[i] = x / b0.w; ys[i] = y / b0.h
                // Let the template follow slow changes in lighting and angle.
                val n0 = extract(b0, x, y, 7)
                for (k in t0.indices) t0[k] = t0[k] * 0.85f + n0[k] * 0.15f
                val n1 = extract(b1, x / 2, y / 2, 4)
                for (k in t1.indices) t1[k] = t1[k] * 0.85f + n1[k] * 0.15f
                done++
                onProgress((done.toFloat() / n).coerceAtMost(1f))
                i += dir
            }
        }
        TrackPath(xs, ys)
    }
}

/** Warp Stabilizer: smooths the camera path and returns the per-frame correction plus a constant crop. */
fun stabilize(g: GlobalMotion, smoothness: Float, heightOverWidth: Float): Stabilization {
    val n = g.frames
    val theta = FloatArray(n); val logS = FloatArray(n)
    var prev = 0f
    for (i in 0 until n) {
        var a = atan2(g.mIm[i], g.mRe[i])
        while (a - prev > Math.PI) a -= (2 * Math.PI).toFloat()
        while (a - prev < -Math.PI) a += (2 * Math.PI).toFloat()
        theta[i] = a; prev = a
        logS[i] = ln(hypot(g.mRe[i], g.mIm[i]).coerceAtLeast(1e-4f))
    }
    val sigma = 1f + smoothness * 20f
    fun smooth(src: FloatArray): FloatArray {
        val r = (sigma * 3).toInt()
        return FloatArray(n) { i ->
            var acc = 0f; var wsum = 0f
            for (d in -r..r) {
                val j = (i + d).coerceIn(0, n - 1)
                val w = exp(-(d * d) / (2 * sigma * sigma))
                acc += src[j] * w; wsum += w
            }
            acc / wsum
        }
    }
    val sTheta = smooth(theta); val sLog = smooth(logS); val sCx = smooth(g.cRe); val sCy = smooth(g.cIm)
    val kRe = FloatArray(n); val kIm = FloatArray(n); val kcRe = FloatArray(n); val kcIm = FloatArray(n)
    for (i in 0 until n) {
        val ss = exp(sLog[i]); val smRe = ss * cos(sTheta[i]); val smIm = ss * sin(sTheta[i])
        // K = S ∘ T⁻¹: K(z) = (sm/m)·z + (sc − (sm/m)·c)
        val d = g.mRe[i] * g.mRe[i] + g.mIm[i] * g.mIm[i]
        val qRe = (smRe * g.mRe[i] + smIm * g.mIm[i]) / d
        val qIm = (smIm * g.mRe[i] - smRe * g.mIm[i]) / d
        kRe[i] = qRe; kIm[i] = qIm
        kcRe[i] = sCx[i] - (qRe * g.cRe[i] - qIm * g.cIm[i])
        kcIm[i] = sCy[i] - (qRe * g.cIm[i] + qIm * g.cRe[i])
    }
    // Smallest constant zoom that keeps every corrected frame covering the view.
    val hw = 0.5f; val hh = 0.5f * heightOverWidth
    var crop = 1f
    search@ while (crop < 1.6f) {
        for (i in 0 until n) {
            val d = kRe[i] * kRe[i] + kIm[i] * kIm[i]
            for (sx in floatArrayOf(-hw, hw)) for (sy in floatArrayOf(-hh, hh)) {
                val zx = sx / crop - kcRe[i]; val zy = sy / crop - kcIm[i]
                val srcX = (zx * kRe[i] + zy * kIm[i]) / d
                val srcY = (zy * kRe[i] - zx * kIm[i]) / d
                if (abs(srcX) > hw || abs(srcY) > hh) { crop += 0.01f; continue@search }
            }
        }
        break
    }
    return Stabilization(smoothness, kRe, kIm, kcRe, kcIm, min(crop, 1.6f))
}

/**
 * Track Camera: the comp camera that moves overlay layers exactly like the scene in the footage.
 * [fRe]/[fIm] map normalised footage coordinates to comp pixels (fit size × layer scale, rotated),
 * [gx]/[gy] is the footage centre in comp pixels. Returns point of interest (px), zoom and roll (°).
 */
fun cameraForScene(g: GlobalMotion, i: Int, fRe: Float, fIm: Float, gx: Float, gy: Float, cx: Float, cy: Float): FloatArray {
    val k = i.coerceIn(0, g.frames - 1)
    val mRe = g.mRe[k]; val mIm = g.mIm[k]
    // Scene transform in comp space: S(w) = m·w + d, with d = f·c + g·(1 − m).
    val fcRe = fRe * g.cRe[k] - fIm * g.cIm[k]; val fcIm = fRe * g.cIm[k] + fIm * g.cRe[k]
    val omRe = 1f - mRe; val omIm = -mIm
    val dRe = fcRe + gx * omRe - gy * omIm; val dIm = fcIm + gx * omIm + gy * omRe
    // Camera C(x) = centre + A·(x − p) equals S when A = m and p = (centre − d) / m.
    val nRe = cx - dRe; val nIm = cy - dIm
    val den = (mRe * mRe + mIm * mIm).coerceAtLeast(1e-6f)
    val pRe = (nRe * mRe + nIm * mIm) / den; val pIm = (nIm * mRe - nRe * mIm) / den
    return floatArrayOf(pRe, pIm, sqrt(den), -Math.toDegrees(atan2(mIm, mRe).toDouble()).toFloat())
}

internal fun gaussianSmooth(values: FloatArray, radius: Int): FloatArray {
    if (radius <= 0) return values
    val sigma = radius / 2f
    return FloatArray(values.size) { i ->
        var acc = 0f; var wsum = 0f
        for (d in -radius..radius) {
            val j = i + d
            if (j < 0 || j >= values.size || values[j].isNaN()) continue
            val w = exp(-(d * d) / (2 * sigma * sigma))
            acc += values[j] * w; wsum += w
        }
        if (wsum == 0f) values[i] else acc / wsum
    }
}

internal fun clampUnit(v: Float) = max(0f, min(1f, v))
