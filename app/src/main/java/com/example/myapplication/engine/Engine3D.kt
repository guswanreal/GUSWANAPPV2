package com.example.myapplication.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// A tiny software 3D renderer for Compose Canvas: flat-shaded meshes, painter's sort,
// back-face culling, orbit camera and tap picking. No OpenGL needed.

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    infix fun mul(o: Vec3) = Vec3(x * o.x, y * o.y, z * o.z)
    fun length() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) this else Vec3(x / l, y / l, z / l)
    }

    /** Euler rotation in degrees, applied X then Y then Z. */
    fun rotated(deg: Vec3): Vec3 {
        var p = this
        if (deg.x != 0f) {
            val a = deg.x.toRad(); val c = cos(a); val s = sin(a)
            p = Vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c)
        }
        if (deg.y != 0f) {
            val a = deg.y.toRad(); val c = cos(a); val s = sin(a)
            p = Vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
        }
        if (deg.z != 0f) {
            val a = deg.z.toRad(); val c = cos(a); val s = sin(a)
            p = Vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z)
        }
        return p
    }

    companion object {
        val Zero = Vec3(0f, 0f, 0f)
        val One = Vec3(1f, 1f, 1f)
    }
}

fun Float.toRad() = (this * PI / 180.0).toFloat()

/** [doubleSided] meshes skip back-face culling; imported files often have inconsistent winding. */
class Mesh(val vertices: List<Vec3>, val faces: List<IntArray>, val doubleSided: Boolean = false) {
    val triangleCount: Int get() = faces.sumOf { it.size - 2 }
}

/** Collects faces and fixes their winding so every normal points along an outward hint. */
private class MeshBuilder {
    val vertices = ArrayList<Vec3>()
    val faces = ArrayList<IntArray>()

    fun vertex(v: Vec3): Int { vertices += v; return vertices.lastIndex }

    fun face(outward: Vec3, vararg idx: Int) {
        val a = vertices[idx[0]]; val b = vertices[idx[1]]; val c = vertices[idx[idx.size - 1]]
        var n = (b - a) cross (c - a)
        if (n.length() < 1e-7f && idx.size > 3) n = (vertices[idx[2]] - a) cross (c - a)
        faces += if ((n dot outward) < 0f) idx.reversedArray() else idx
    }

    fun build() = Mesh(vertices.toList(), faces.toList())
}

object Primitives {
    val cube: Mesh by lazy { box() }
    val sphere: Mesh by lazy { sphere(14, 22) }
    val lowSphere: Mesh by lazy { sphere(8, 12) }
    val cylinder: Mesh by lazy { cylinder(22) }
    val cone: Mesh by lazy { cone(22) }
    val torus: Mesh by lazy { torus(1f, 0.38f, 26, 12) }
    val pyramid: Mesh by lazy { cone(4) }
    val gem: Mesh by lazy { icosahedron() }
    val plane: Mesh by lazy { plane() }

    private fun box(): Mesh {
        val b = MeshBuilder()
        for (i in 0 until 8) b.vertex(Vec3(if (i and 1 != 0) 1f else -1f, if (i and 2 != 0) 1f else -1f, if (i and 4 != 0) 1f else -1f))
        b.face(Vec3(0f, 0f, 1f), 4, 5, 7, 6)
        b.face(Vec3(0f, 0f, -1f), 1, 0, 2, 3)
        b.face(Vec3(1f, 0f, 0f), 5, 1, 3, 7)
        b.face(Vec3(-1f, 0f, 0f), 0, 4, 6, 2)
        b.face(Vec3(0f, 1f, 0f), 6, 7, 3, 2)
        b.face(Vec3(0f, -1f, 0f), 0, 1, 5, 4)
        return b.build()
    }

    private fun plane(): Mesh {
        val b = MeshBuilder()
        b.vertex(Vec3(-1f, 0f, -1f)); b.vertex(Vec3(1f, 0f, -1f)); b.vertex(Vec3(1f, 0f, 1f)); b.vertex(Vec3(-1f, 0f, 1f))
        b.face(Vec3(0f, 1f, 0f), 0, 1, 2, 3)
        return b.build()
    }

    private fun sphere(rings: Int, segments: Int): Mesh {
        val b = MeshBuilder()
        val top = b.vertex(Vec3(0f, 1f, 0f))
        val grid = Array(rings - 1) { r ->
            val phi = PI * (r + 1) / rings
            IntArray(segments) { s ->
                val th = 2 * PI * s / segments
                b.vertex(Vec3((sin(phi) * cos(th)).toFloat(), cos(phi).toFloat(), (sin(phi) * sin(th)).toFloat()))
            }
        }
        val bottom = b.vertex(Vec3(0f, -1f, 0f))
        for (s in 0 until segments) {
            val n = (s + 1) % segments
            b.face(centroid(b, top, grid[0][s], grid[0][n]), top, grid[0][s], grid[0][n])
            val last = grid.last()
            b.face(centroid(b, bottom, last[n], last[s]), bottom, last[n], last[s])
            for (r in 0 until rings - 2) {
                val q = intArrayOf(grid[r][s], grid[r][n], grid[r + 1][n], grid[r + 1][s])
                b.face(centroid(b, *q), *q)
            }
        }
        return b.build()
    }

    private fun cylinder(segments: Int): Mesh {
        val b = MeshBuilder()
        val bottom = IntArray(segments) { s -> val t = 2 * PI * s / segments; b.vertex(Vec3(cos(t).toFloat(), -1f, sin(t).toFloat())) }
        val top = IntArray(segments) { s -> val t = 2 * PI * s / segments; b.vertex(Vec3(cos(t).toFloat(), 1f, sin(t).toFloat())) }
        for (s in 0 until segments) {
            val n = (s + 1) % segments
            val q = intArrayOf(bottom[s], bottom[n], top[n], top[s])
            val c = centroid(b, *q)
            b.face(Vec3(c.x, 0f, c.z), *q)
        }
        b.face(Vec3(0f, 1f, 0f), *top)
        b.face(Vec3(0f, -1f, 0f), *bottom)
        return b.build()
    }

    private fun cone(segments: Int): Mesh {
        val b = MeshBuilder()
        val apex = b.vertex(Vec3(0f, 1f, 0f))
        val ring = IntArray(segments) { s -> val t = 2 * PI * s / segments + PI / segments; b.vertex(Vec3(cos(t).toFloat(), -1f, sin(t).toFloat())) }
        for (s in 0 until segments) {
            val n = (s + 1) % segments
            val c = centroid(b, apex, ring[s], ring[n])
            b.face(Vec3(c.x, 0.4f, c.z), apex, ring[s], ring[n])
        }
        b.face(Vec3(0f, -1f, 0f), *ring)
        return b.build()
    }

    fun torus(major: Float, minor: Float, segments: Int, tube: Int): Mesh {
        val b = MeshBuilder()
        val grid = Array(segments) { s ->
            val u = 2 * PI * s / segments
            IntArray(tube) { t ->
                val v = 2 * PI * t / tube
                val r = major + minor * cos(v)
                b.vertex(Vec3((r * cos(u)).toFloat(), (minor * sin(v)).toFloat(), (r * sin(u)).toFloat()))
            }
        }
        for (s in 0 until segments) for (t in 0 until tube) {
            val sn = (s + 1) % segments; val tn = (t + 1) % tube
            val q = intArrayOf(grid[s][t], grid[sn][t], grid[sn][tn], grid[s][tn])
            val c = centroid(b, *q)
            val ringPoint = Vec3(c.x, 0f, c.z).normalized() * major
            b.face(c - ringPoint, *q)
        }
        return b.build()
    }

    private fun icosahedron(): Mesh {
        val b = MeshBuilder()
        val t = ((1 + sqrt(5.0)) / 2).toFloat()
        listOf(
            Vec3(-1f, t, 0f), Vec3(1f, t, 0f), Vec3(-1f, -t, 0f), Vec3(1f, -t, 0f),
            Vec3(0f, -1f, t), Vec3(0f, 1f, t), Vec3(0f, -1f, -t), Vec3(0f, 1f, -t),
            Vec3(t, 0f, -1f), Vec3(t, 0f, 1f), Vec3(-t, 0f, -1f), Vec3(-t, 0f, 1f),
        ).forEach { b.vertex(it.normalized()) }
        val f = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )
        for (i in f.indices step 3) b.face(centroid(b, f[i], f[i + 1], f[i + 2]), f[i], f[i + 1], f[i + 2])
        return b.build()
    }

    private fun centroid(b: MeshBuilder, vararg idx: Int): Vec3 {
        var c = Vec3.Zero
        idx.forEach { c += b.vertices[it] }
        return c * (1f / idx.size)
    }
}

/** One mesh placed inside a model. A null color means "use the object's material color". */
data class Part(
    val mesh: Mesh,
    val offset: Vec3 = Vec3.Zero,
    val scale: Vec3 = Vec3.One,
    val rotation: Vec3 = Vec3.Zero,
    val color: Color? = null,
)

data class Transform(
    val position: Vec3 = Vec3.Zero,
    val rotation: Vec3 = Vec3.Zero,
    val scale: Vec3 = Vec3.One,
)

data class RenderItem(
    val parts: List<Part>,
    val transform: Transform,
    val color: Color,
    val tag: Int,
)

data class Camera(
    val yaw: Float = 35f,
    val pitch: Float = 22f,
    val distance: Float = 7f,
    val target: Vec3 = Vec3(0f, 0.6f, 0f),
)

enum class ShadingMode(val label: String) { Shaded("Shaded"), Clay("Clay"), Wireframe("Wire"), Xray("X-Ray") }

/** Screen-space polygon remembered from the last frame, used for tap picking. */
class HitPoly(val tag: Int, val xs: FloatArray, val ys: FloatArray) {
    fun contains(p: Offset): Boolean {
        var inside = false
        var j = xs.lastIndex
        for (i in xs.indices) {
            if ((ys[i] > p.y) != (ys[j] > p.y) &&
                p.x < (xs[j] - xs[i]) * (p.y - ys[i]) / (ys[j] - ys[i]) + xs[i]
            ) inside = !inside
            j = i
        }
        return inside
    }
}

fun pick(hits: List<HitPoly>, p: Offset): Int? = hits.lastOrNull { it.contains(p) }?.tag

/**
 * Reusable buffers for [drawScene], one set per thread (preview on the UI thread, export on its own).
 * Previews draw every frame, so faces live in flat arrays instead of an object per face: corners of
 * face i are at `[start[i], start[i + 1])` in [px]/[py].
 */
private class SceneBuffers {
    var wx = FloatArray(256); var wy = FloatArray(256); var wz = FloatArray(256)
    var camX = FloatArray(256); var camY = FloatArray(256); var camZ = FloatArray(256)
    var px = FloatArray(1024); var py = FloatArray(1024)
    var start = IntArray(513); var depth = FloatArray(512); var argb = IntArray(512); var tag = IntArray(512)
    var order = LongArray(512)
    var verts = FloatArray(3072); var colors = IntArray(1536)
    var faceCount = 0
    var cornerCount = 0

    fun reset() { faceCount = 0; cornerCount = 0 }

    fun ensureVertices(n: Int) {
        if (wx.size >= n) return
        val c = grow(wx.size, n)
        wx = FloatArray(c); wy = FloatArray(c); wz = FloatArray(c)
        camX = FloatArray(c); camY = FloatArray(c); camZ = FloatArray(c)
    }

    fun addFace(f: IntArray, cx: Float, cy: Float, focal: Float, faceDepth: Float, color: Int, faceTag: Int) {
        if (faceCount == depth.size) {
            val c = grow(depth.size, faceCount + 1)
            depth = depth.copyOf(c); argb = argb.copyOf(c); tag = tag.copyOf(c); start = start.copyOf(c + 1); order = LongArray(c)
        }
        if (cornerCount + f.size > px.size) {
            val c = grow(px.size, cornerCount + f.size)
            px = px.copyOf(c); py = py.copyOf(c)
        }
        val s = cornerCount
        for (k in f.indices) {
            val i = f[k]
            px[s + k] = cx + focal * camX[i] / -camZ[i]
            py[s + k] = cy - focal * camY[i] / -camZ[i]
        }
        cornerCount += f.size
        depth[faceCount] = faceDepth; argb[faceCount] = color; tag[faceCount] = faceTag
        start[faceCount + 1] = cornerCount
        faceCount++
    }

    fun ensureTriangles(n: Int) {
        if (colors.size >= n * 3) return
        val c = grow(colors.size / 3, n)
        verts = FloatArray(c * 6); colors = IntArray(c * 3)
    }

    private fun grow(current: Int, needed: Int) = max(needed, current * 2)
}

private val Buffers = ThreadLocal.withInitial { SceneBuffers() }

/** Maps a float to an int with the same ordering, so depths can be sorted as primitives. */
private fun sortableBits(f: Float): Int {
    val bits = java.lang.Float.floatToIntBits(f)
    return bits xor ((bits shr 31) and 0x7FFFFFFF)
}

/** Columns of the rotation matrix equivalent to [Vec3.rotated], so vertices can be rotated without trig. */
private fun rotationColumns(deg: Vec3): FloatArray {
    val a = Vec3(1f, 0f, 0f).rotated(deg); val b = Vec3(0f, 1f, 0f).rotated(deg); val c = Vec3(0f, 0f, 1f).rotated(deg)
    return floatArrayOf(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z)
}

/** One colour channel: base lit value blended toward white by [toWhite], as an 8-bit int. */
private fun channel(lit: Float, toWhite: Float): Int {
    val v = (lit + (1f - lit) * toWhite.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    return (v * 255f + 0.5f).toInt()
}

private val LightDir = Vec3(-0.45f, 0.85f, 0.55f).normalized()
private val ClayColor = Color(0xFFC9CED8)

fun DrawScope.drawScene(
    items: List<RenderItem>,
    camera: Camera,
    mode: ShadingMode = ShadingMode.Shaded,
    selectedTag: Int? = null,
    showGrid: Boolean = true,
    gridColor: Color = Color(0x22FFFFFF),
    accent: Color = Color(0xFFEC4899),
    hits: MutableList<HitPoly>? = null,
    zoom: Float = 1f,
    center: Offset = Offset(size.width / 2f, size.height / 2f),
    viewExtent: Float = min(size.width, size.height),
) {
    val cx = center.x
    val cy = center.y
    val focal = viewExtent * 1.1f * zoom
    val near = 0.1f
    val cyaw = cos(camera.yaw.toRad()); val syaw = sin(camera.yaw.toRad())
    val cpit = cos(camera.pitch.toRad()); val spit = sin(camera.pitch.toRad())

    fun toCamera(p: Vec3): Vec3 {
        val d = p - camera.target
        val x1 = d.x * cyaw - d.z * syaw
        val z1 = d.x * syaw + d.z * cyaw
        val y2 = d.y * cpit - z1 * spit
        val z2 = d.y * spit + z1 * cpit
        return Vec3(x1, y2, z2 - camera.distance)
    }

    fun projX(v: Vec3) = cx + focal * v.x / -v.z
    fun projY(v: Vec3) = cy - focal * v.y / -v.z

    if (showGrid) {
        val extent = 6
        for (i in -extent..extent) {
            val major = i == 0
            val c = if (major) gridColor.copy(alpha = gridColor.alpha * 2.2f) else gridColor
            for (axis in 0..1) {
                val a = if (axis == 0) Vec3(i.toFloat(), 0f, -extent.toFloat()) else Vec3(-extent.toFloat(), 0f, i.toFloat())
                val b = if (axis == 0) Vec3(i.toFloat(), 0f, extent.toFloat()) else Vec3(extent.toFloat(), 0f, i.toFloat())
                val ca = toCamera(a); val cb = toCamera(b)
                if (ca.z < -near && cb.z < -near) {
                    drawLine(c, Offset(projX(ca), projY(ca)), Offset(projX(cb), projY(cb)), strokeWidth = if (major) 1.6f else 1f)
                }
            }
        }
    }

    // Hot loop: runs every frame for every spinning thumbnail and 3D layer, so it works on flat
    // arrays reused across frames, with precomputed rotation matrices instead of trig per vertex.
    val buf = Buffers.get()!!
    buf.reset()
    val see = mode == ShadingMode.Wireframe || mode == ShadingMode.Xray
    val (shadeBase, shadeGain) = if (mode == ShadingMode.Clay) 0.32f to 0.68f else 0.26f to 0.74f
    val tx = camera.target.x; val ty = camera.target.y; val tz = camera.target.z
    for (item in items) {
        val t = item.transform
        val ir = rotationColumns(t.rotation)
        for (part in item.parts) {
            val base = if (mode == ShadingMode.Clay) ClayColor else part.color ?: item.color
            val br = base.red; val bg = base.green; val bb = base.blue
            val alpha = (base.alpha * 255f + 0.5f).toInt() shl 24
            val pr = rotationColumns(part.rotation)
            val twoSided = part.mesh.doubleSided
            val verts = part.mesh.vertices
            val n = verts.size
            buf.ensureVertices(n)
            val wx = buf.wx; val wy = buf.wy; val wz = buf.wz
            val camX = buf.camX; val camY = buf.camY; val camZ = buf.camZ
            for (i in 0 until n) {
                val v = verts[i]
                val sx = v.x * part.scale.x; val sy = v.y * part.scale.y; val sz = v.z * part.scale.z
                val lx = (sx * pr[0] + sy * pr[3] + sz * pr[6] + part.offset.x) * t.scale.x
                val ly = (sx * pr[1] + sy * pr[4] + sz * pr[7] + part.offset.y) * t.scale.y
                val lz = (sx * pr[2] + sy * pr[5] + sz * pr[8] + part.offset.z) * t.scale.z
                val x = lx * ir[0] + ly * ir[3] + lz * ir[6] + t.position.x
                val y = lx * ir[1] + ly * ir[4] + lz * ir[7] + t.position.y
                val z = lx * ir[2] + ly * ir[5] + lz * ir[8] + t.position.z
                wx[i] = x; wy[i] = y; wz[i] = z
                val dx = x - tx; val dy = y - ty; val dz = z - tz
                val z1 = dx * syaw + dz * cyaw
                camX[i] = dx * cyaw - dz * syaw
                camY[i] = dy * cpit - z1 * spit
                camZ[i] = dy * spit + z1 * cpit - camera.distance
            }
            for (f in part.mesh.faces) {
                var behind = false
                var depth = 0f
                for (i in f) { val z = camZ[i]; if (z > -near) behind = true; depth += z }
                if (behind) continue
                depth /= f.size
                val a = f[0]; val b = f[1]; val c = f[f.size - 1]
                val ax = camX[a]; val ay = camY[a]; val az = camZ[a]
                val e1x = camX[b] - ax; val e1y = camY[b] - ay; val e1z = camZ[b] - az
                val e2x = camX[c] - ax; val e2y = camY[c] - ay; val e2z = camZ[c] - az
                val ncx = e1y * e2z - e1z * e2y; val ncy = e1z * e2x - e1x * e2z; val ncz = e1x * e2y - e1y * e2x
                val facing = -(ncx * ax + ncy * ay + ncz * az)
                if (facing <= 0f && !see && !twoSided) continue
                val f1x = wx[b] - wx[a]; val f1y = wy[b] - wy[a]; val f1z = wz[b] - wz[a]
                val f2x = wx[c] - wx[a]; val f2y = wy[c] - wy[a]; val f2z = wz[c] - wz[a]
                // A back face seen on a two-sided mesh is lit from its visible side.
                val flip = if (facing < 0f) -1f else 1f
                val nx = (f1y * f2z - f1z * f2y) * flip; val ny = (f1z * f2x - f1x * f2z) * flip; val nz = (f1x * f2y - f1y * f2x) * flip
                val nLen = sqrt(nx * nx + ny * ny + nz * nz)
                val diffuse = if (nLen < 1e-6f) 0f else max(0f, (nx * LightDir.x + ny * LightDir.y + nz * LightDir.z) / nLen)
                val denom = sqrt((ncx * ncx + ncy * ncy + ncz * ncz) * (ax * ax + ay * ay + az * az))
                val r0 = if (denom < 1e-12f) 1f else (1f - abs(facing) / denom).coerceIn(0f, 1f)
                val d2 = diffuse * diffuse; val d4 = d2 * d2; val d8 = d4 * d4
                val highlight = r0 * r0 * r0 * 0.18f + d8 * d8 * d8 * 0.35f // rim³ + diffuse²⁴
                val shade = (shadeBase + shadeGain * diffuse).coerceIn(0f, 1f)
                buf.addFace(
                    f, cx, cy, focal, depth,
                    alpha or (channel(br * shade, highlight) shl 16) or (channel(bg * shade, highlight) shl 8) or channel(bb * shade, highlight),
                    item.tag,
                )
            }
        }
    }
    // Painter's sort on packed primitive keys: no boxing, which matters for imported meshes with thousands of faces.
    val faceCount = buf.faceCount
    val order = buf.order
    for (i in 0 until faceCount) order[i] = (sortableBits(buf.depth[i]).toLong() shl 32) or i.toLong()
    java.util.Arrays.sort(order, 0, faceCount)
    val px = buf.px; val py = buf.py; val start = buf.start

    hits?.clear()
    if (mode == ShadingMode.Shaded || mode == ShadingMode.Clay) {
        // One drawVertices call for the whole scene: far cheaper than a Path per face,
        // and without anti-aliasing there are no hairline seams between triangles.
        var triCount = 0
        for (i in 0 until faceCount) triCount += start[i + 1] - start[i] - 2
        buf.ensureTriangles(triCount)
        val verts = buf.verts; val colors = buf.colors
        var vi = 0; var ci = 0
        for (j in 0 until faceCount) {
            val fi = (order[j] and 0xFFFFFFFFL).toInt()
            val s = start[fi]; val e = start[fi + 1]
            val argb = buf.argb[fi]
            for (k in s + 1 until e - 1) {
                verts[vi++] = px[s]; verts[vi++] = py[s]
                verts[vi++] = px[k]; verts[vi++] = py[k]
                verts[vi++] = px[k + 1]; verts[vi++] = py[k + 1]
                colors[ci++] = argb; colors[ci++] = argb; colors[ci++] = argb
            }
            hits?.add(HitPoly(buf.tag[fi], px.copyOfRange(s, e), py.copyOfRange(s, e)))
        }
        // The canvas copies the vertices when it records them, so the buffers are free to reuse next frame.
        if (triCount > 0) drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawVertices(
                android.graphics.Canvas.VertexMode.TRIANGLES, triCount * 6, verts, 0,
                null, 0, colors, 0, null, 0, 0, VertexPaint,
            )
        }
        if (selectedTag != null) {
            val path = Path()
            for (j in 0 until faceCount) {
                val fi = (order[j] and 0xFFFFFFFFL).toInt()
                if (buf.tag[fi] != selectedTag) continue
                val s = start[fi]; val e = start[fi + 1]
                path.moveTo(px[s], py[s])
                for (k in s + 1 until e) path.lineTo(px[k], py[k])
                path.close()
            }
            drawPath(path, accent.copy(alpha = 0.45f), style = Stroke(1.2f))
        }
        return
    }

    val path = Path()
    for (j in 0 until faceCount) {
        val fi = (order[j] and 0xFFFFFFFFL).toInt()
        val s = start[fi]; val e = start[fi + 1]
        path.reset()
        path.moveTo(px[s], py[s])
        for (k in s + 1 until e) path.lineTo(px[k], py[k])
        path.close()
        val faceTag = buf.tag[fi]
        val color = Color(buf.argb[fi])
        val selected = faceTag == selectedTag
        if (mode == ShadingMode.Wireframe) {
            drawPath(path, if (selected) accent else color.copy(alpha = 0.9f), style = Stroke(1.1f))
        } else {
            drawPath(path, color.copy(alpha = 0.16f))
            drawPath(path, (if (selected) accent else color).copy(alpha = 0.5f), style = Stroke(0.8f))
        }
        hits?.add(HitPoly(faceTag, px.copyOfRange(s, e), py.copyOfRange(s, e)))
    }
}

private val VertexPaint = android.graphics.Paint().apply { isAntiAlias = false }

/** Draws the little XYZ orientation gizmo found in every 3D app. */
fun DrawScope.drawAxisGizmo(camera: Camera, center: Offset, radius: Float) {
    val cyaw = cos(camera.yaw.toRad()); val syaw = sin(camera.yaw.toRad())
    val cpit = cos(camera.pitch.toRad()); val spit = sin(camera.pitch.toRad())
    val axes = listOf(
        Vec3(1f, 0f, 0f) to Color(0xFFEF4444),
        Vec3(0f, 1f, 0f) to Color(0xFF84CC16),
        Vec3(0f, 0f, 1f) to Color(0xFF3B82F6),
    ).map { (v, c) ->
        val x1 = v.x * cyaw - v.z * syaw
        val z1 = v.x * syaw + v.z * cyaw
        val y2 = v.y * cpit - z1 * spit
        val z2 = v.y * spit + z1 * cpit
        Triple(Offset(center.x + x1 * radius, center.y - y2 * radius), z2, c)
    }.sortedBy { it.second }
    drawCircle(Color(0x33000000), radius * 1.35f, center)
    for ((end, _, c) in axes) {
        drawLine(c, center, end, strokeWidth = 3f)
        drawCircle(c, 5f, end)
    }
}

/** Serialises world-space geometry to Wavefront OBJ, readable by Blender, Maya, etc. */
fun List<RenderItem>.toObj(): String = buildString {
    appendLine("# Exported from Guswaneditor")
    var base = 1
    for ((index, item) in this@toObj.withIndex()) {
        appendLine("o object_${index + 1}")
        val t = item.transform
        for (part in item.parts) {
            for (v in part.mesh.vertices) {
                val local = (v mul part.scale).rotated(part.rotation) + part.offset
                val w = (local mul t.scale).rotated(t.rotation) + t.position
                appendLine("v %.5f %.5f %.5f".format(java.util.Locale.US, w.x, w.y, w.z))
            }
            for (f in part.mesh.faces) appendLine(f.joinToString(" ", prefix = "f ") { (it + base).toString() })
            base += part.mesh.vertices.size
        }
    }
}
