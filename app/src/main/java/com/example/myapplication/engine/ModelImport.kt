package com.example.myapplication.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.StringTokenizer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Imports 3D files (glTF/GLB, OBJ+MTL, STL, PLY) into Guswaneditor's flat-shaded mesh format,
// the way Blender's importers read them: node hierarchy baked, materials reduced to base colours.

class ImportException(message: String) : Exception(message)

/** A picked file. Siblings picked alongside it (.mtl, .bin, textures) are resolved by name. */
class SourceFile(val name: String, val bytes: ByteArray)

/** Mesh data straight from a file: flat xyz positions and triangle indices. */
class RawPart(val positions: FloatArray, val indices: IntArray, val color: Color?)

object ModelImporter {
    /** The software renderer stays fluid on low-end phones up to roughly this many triangles. */
    const val MAX_TRIANGLES = 6000
    private const val MAX_SOURCE_TRIANGLES = 3_000_000
    val extensions = listOf("glb", "gltf", "obj", "stl", "ply")

    class Result(val model: LibraryModel, val sourceTriangles: Int, val triangles: Int)

    fun import(files: List<SourceFile>, id: String): Result {
        val main = files.firstOrNull { ext(it.name) in extensions }
            ?: throw ImportException("Format belum didukung. Pilih file .glb, .gltf, .obj, .stl, atau .ply. Dari Blender: File › Export › glTF 2.0 (.glb).")
        val siblings = files.associateBy { baseName(it.name) }
        val parts = when (ext(main.name)) {
            "glb" -> Gltf.parseGlb(main.bytes, siblings)
            "gltf" -> Gltf.parse(String(main.bytes, Charsets.UTF_8), null, siblings)
            "obj" -> Obj.parse(String(main.bytes, Charsets.UTF_8), siblings)
            "stl" -> zUpToYUp(Stl.parse(main.bytes))
            else -> zUpToYUp(Ply.parse(main.bytes))
        }.filter { it.indices.size >= 3 && it.positions.isNotEmpty() }
        if (parts.isEmpty()) throw ImportException("File tidak berisi mesh segitiga yang bisa dibaca.")
        val source = parts.sumOf { it.indices.size / 3 }
        if (source > MAX_SOURCE_TRIANGLES) throw ImportException("Model terlalu besar (${"%,d".format(source)} segitiga). Kurangi dengan Decimate di Blender dulu.")
        val normalized = normalize(parts)
        val reduced = if (source > MAX_TRIANGLES) decimate(normalized, MAX_TRIANGLES) else normalized
        val name = main.name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Model" }.take(40)
        return Result(toModel(id, name, reduced), source, reduced.sumOf { it.indices.size / 3 })
    }

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
}

internal fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\').lowercase()

// ---------------------------------------------------------------- Post-processing

private fun zUpToYUp(parts: List<RawPart>) = parts.map { p ->
    val out = FloatArray(p.positions.size)
    for (i in p.positions.indices step 3) {
        out[i] = p.positions[i]; out[i + 1] = p.positions[i + 2]; out[i + 2] = -p.positions[i + 1]
    }
    RawPart(out, p.indices, p.color)
}

/** Centres the model and scales its largest dimension to 2 units, matching the built-in library. */
private fun normalize(parts: List<RawPart>): List<RawPart> {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    for (p in parts) for (i in p.positions.indices step 3) {
        val x = p.positions[i]; val y = p.positions[i + 1]; val z = p.positions[i + 2]
        if (x.isNaN() || y.isNaN() || z.isNaN()) continue
        minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y); minZ = min(minZ, z); maxZ = max(maxZ, z)
    }
    val extent = max(maxX - minX, max(maxY - minY, maxZ - minZ)).takeIf { it > 1e-9f } ?: 1f
    val k = 2f / extent
    val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val cz = (minZ + maxZ) / 2
    return parts.map { p ->
        val out = FloatArray(p.positions.size)
        for (i in p.positions.indices step 3) {
            out[i] = (p.positions[i] - cx) * k; out[i + 1] = (p.positions[i + 1] - cy) * k; out[i + 2] = (p.positions[i + 2] - cz) * k
        }
        RawPart(out, p.indices, p.color)
    }
}

/** Vertex clustering: snaps vertices to a grid that coarsens until the triangle budget fits. */
private fun decimate(parts: List<RawPart>, budget: Int): List<RawPart> {
    var grid = 160
    var result = parts
    while (grid >= 3) {
        result = parts.map { cluster(it, grid) }
        if (result.sumOf { it.indices.size / 3 } <= budget) break
        grid = (grid * 0.82f).toInt()
    }
    return result.filter { it.indices.isNotEmpty() }
}

private fun cluster(part: RawPart, grid: Int): RawPart {
    val cell = 2.02f / grid
    val n = part.positions.size / 3
    val ids = HashMap<Long, Int>(n / 2 + 16)
    val remap = IntArray(n)
    val sum = FloatBuilder()
    val counts = IntBuilder()
    for (i in 0 until n) {
        val gx = floor((part.positions[i * 3] + 1.01f) / cell).toLong()
        val gy = floor((part.positions[i * 3 + 1] + 1.01f) / cell).toLong()
        val gz = floor((part.positions[i * 3 + 2] + 1.01f) / cell).toLong()
        val key = (gx shl 42) or (gy shl 21) or gz
        val id = ids.getOrPut(key) { counts.add(0); sum.add(0f); sum.add(0f); sum.add(0f); counts.size - 1 }
        remap[i] = id
        sum.data[id * 3] += part.positions[i * 3]; sum.data[id * 3 + 1] += part.positions[i * 3 + 1]; sum.data[id * 3 + 2] += part.positions[i * 3 + 2]
        counts.data[id]++
    }
    val pos = FloatArray(counts.size * 3) { sum.data[it] / counts.data[it / 3] }
    val tris = IntBuilder()
    val seen = HashSet<Long>()
    for (t in part.indices.indices step 3) {
        val a = remap[part.indices[t]]; val b = remap[part.indices[t + 1]]; val c = remap[part.indices[t + 2]]
        if (a == b || b == c || a == c) continue
        // Drop duplicate triangles the snapping created, regardless of winding.
        val lo = min(a, min(b, c)); val hi = max(a, max(b, c)); val mid = a + b + c - lo - hi
        if (!seen.add((lo.toLong() shl 42) or (mid.toLong() shl 21) or hi.toLong())) continue
        tris.add(a); tris.add(b); tris.add(c)
    }
    return RawPart(pos, tris.toArray(), part.color)
}

private val ImportedGrey = Color(0xFFB8BCC6)

private fun toModel(id: String, name: String, parts: List<RawPart>): LibraryModel {
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    parts.forEach { p -> for (i in 1 until p.positions.size step 3) { minY = min(minY, p.positions[i]); maxY = max(maxY, p.positions[i]) } }
    val base = parts.firstNotNullOfOrNull { it.color } ?: ImportedGrey
    val meshParts = parts.map { p ->
        val verts = List(p.positions.size / 3) { Vec3(p.positions[it * 3], p.positions[it * 3 + 1], p.positions[it * 3 + 2]) }
        val faces = List(p.indices.size / 3) { intArrayOf(p.indices[it * 3], p.indices[it * 3 + 1], p.indices[it * 3 + 2]) }
        Part(Mesh(verts, faces, doubleSided = true), color = p.color?.takeIf { it != base })
    }
    return LibraryModel(id, name, ModelCategory.Imported, base, meshParts, restHeight = (maxY - minY) / 2f)
}

// ---------------------------------------------------------------- glTF 2.0 / GLB

private object Gltf {
    fun parseGlb(bytes: ByteArray, siblings: Map<String, SourceFile>): List<RawPart> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 20 || bb.getInt(0) != 0x46546C67) throw ImportException("Bukan file GLB yang valid.")
        if (bb.getInt(4) != 2) throw ImportException("Hanya glTF 2.0 yang didukung.")
        var offset = 12
        var json: String? = null
        var bin: ByteBuffer? = null
        while (offset + 8 <= bytes.size) {
            val len = bb.getInt(offset)
            val type = bb.getInt(offset + 4)
            val start = offset + 8
            if (len < 0 || start + len > bytes.size) break
            when (type) {
                0x4E4F534A -> json = String(bytes, start, len, Charsets.UTF_8)
                0x004E4942 -> bin = ByteBuffer.wrap(bytes, start, len).slice().order(ByteOrder.LITTLE_ENDIAN)
            }
            offset = start + len
        }
        return parse(json ?: throw ImportException("GLB tanpa data JSON."), bin, siblings)
    }

    fun parse(jsonText: String, glbBin: ByteBuffer?, siblings: Map<String, SourceFile>): List<RawPart> {
        val root = JSONObject(jsonText)
        root.optJSONArray("extensionsRequired")?.let { req ->
            for (i in 0 until req.length()) {
                val e = req.getString(i)
                if (e == "KHR_draco_mesh_compression" || e == "EXT_meshopt_compression") {
                    throw ImportException("Model ini memakai kompresi ($e) yang belum didukung. Di Blender, ekspor ulang glTF dengan Compression dimatikan.")
                }
            }
        }
        val buffers = root.optJSONArray("buffers").objects().map { b ->
            val uri = b.optString("uri", "")
            when {
                uri.isEmpty() -> glbBin ?: throw ImportException("Buffer biner tidak ditemukan.")
                uri.startsWith("data:") -> ByteBuffer.wrap(Base64.decode(uri.substringAfter(','), Base64.DEFAULT)).order(ByteOrder.LITTLE_ENDIAN)
                else -> siblings[baseName(Uri.decode(uri))]?.let { ByteBuffer.wrap(it.bytes).order(ByteOrder.LITTLE_ENDIAN) }
                    ?: throw ImportException("File pendamping \"$uri\" tidak ikut dipilih. Pilih .gltf bersama file .bin-nya, atau pakai .glb.")
            }
        }
        val views = root.optJSONArray("bufferViews").objects()
        val accessors = root.optJSONArray("accessors").objects()

        fun viewBytes(viewIndex: Int): ByteArray {
            val v = views[viewIndex]
            val buf = buffers[v.getInt("buffer")]
            val out = ByteArray(v.getInt("byteLength"))
            val dup = buf.duplicate(); dup.position(v.optInt("byteOffset", 0)); dup.get(out)
            return out
        }

        fun read(accIndex: Int, normalizedInts: Boolean): Pair<FloatArray, Int> {
            val a = accessors[accIndex]
            val count = a.getInt("count")
            val comps = when (a.getString("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1 }
            val ct = a.getInt("componentType")
            val csize = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            val out = FloatArray(count * comps)
            if (!a.has("bufferView")) return out to comps
            val v = views[a.getInt("bufferView")]
            val buf = buffers[v.getInt("buffer")]
            val stride = v.optInt("byteStride", 0).takeIf { it > 0 } ?: (comps * csize)
            val base = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val norm = normalizedInts && a.optBoolean("normalized", false)
            for (i in 0 until count) for (c in 0 until comps) {
                val at = base + i * stride + c * csize
                out[i * comps + c] = when (ct) {
                    5126 -> buf.getFloat(at)
                    5125 -> (buf.getInt(at).toLong() and 0xFFFFFFFFL).toFloat()
                    5123 -> (buf.getShort(at).toInt() and 0xFFFF).let { if (norm) it / 65535f else it.toFloat() }
                    5122 -> buf.getShort(at).toInt().let { if (norm) max(it / 32767f, -1f) else it.toFloat() }
                    5121 -> (buf.get(at).toInt() and 0xFF).let { if (norm) it / 255f else it.toFloat() }
                    else -> buf.get(at).toInt().let { if (norm) max(it / 127f, -1f) else it.toFloat() }
                }
            }
            return out to comps
        }

        fun readIndices(accIndex: Int): IntArray {
            val a = accessors[accIndex]
            val count = a.getInt("count")
            val out = IntArray(count)
            if (!a.has("bufferView")) return out
            val v = views[a.getInt("bufferView")]
            val buf = buffers[v.getInt("buffer")]
            val ct = a.getInt("componentType")
            val csize = when (ct) { 5121 -> 1; 5123 -> 2; else -> 4 }
            val stride = v.optInt("byteStride", 0).takeIf { it > 0 } ?: csize
            val base = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            for (i in 0 until count) {
                val at = base + i * stride
                out[i] = when (ct) { 5121 -> buf.get(at).toInt() and 0xFF; 5123 -> buf.getShort(at).toInt() and 0xFFFF; else -> buf.getInt(at) }
            }
            return out
        }

        val images = root.optJSONArray("images").objects()
        val textures = root.optJSONArray("textures").objects()
        val textureColors = HashMap<Int, Color?>()
        fun textureColor(texIndex: Int): Color? = textureColors.getOrPut(texIndex) {
            val src = textures.getOrNull(texIndex)?.optInt("source", -1) ?: -1
            val img = images.getOrNull(src) ?: return@getOrPut null
            val bytes = when {
                img.has("bufferView") -> viewBytes(img.getInt("bufferView"))
                img.optString("uri").startsWith("data:") -> Base64.decode(img.getString("uri").substringAfter(','), Base64.DEFAULT)
                img.has("uri") -> siblings[baseName(Uri.decode(img.getString("uri")))]?.bytes
                else -> null
            }
            bytes?.let { averageImageColor(it) }
        }

        val materialColors = root.optJSONArray("materials").objects().map { m ->
            val pbr = m.optJSONObject("pbrMetallicRoughness")
            val f = pbr?.optJSONArray("baseColorFactor")
            var r = 1f; var g = 1f; var b = 1f
            if (f != null && f.length() >= 3) {
                // baseColorFactor is linear; the renderer works in sRGB.
                r = linearToSrgb(f.getDouble(0).toFloat()); g = linearToSrgb(f.getDouble(1).toFloat()); b = linearToSrgb(f.getDouble(2).toFloat())
            }
            val tex = pbr?.optJSONObject("baseColorTexture")?.optInt("index", -1) ?: -1
            val texColor = if (tex >= 0) textureColor(tex) else null
            when {
                texColor != null -> Color(texColor.red * r, texColor.green * g, texColor.blue * b)
                f != null -> Color(r, g, b)
                else -> null
            }
        }

        class Prim(val positions: FloatArray, val indices: IntArray, val color: Color?)
        val meshes = root.optJSONArray("meshes").objects().map { mesh ->
            mesh.optJSONArray("primitives").objects().mapNotNull { p ->
                val attrs = p.optJSONObject("attributes") ?: return@mapNotNull null
                if (!attrs.has("POSITION")) return@mapNotNull null
                val (pos, comps) = read(attrs.getInt("POSITION"), normalizedInts = true)
                if (comps != 3) return@mapNotNull null
                val vertexCount = pos.size / 3
                val raw = if (p.has("indices")) readIndices(p.getInt("indices")) else IntArray(vertexCount) { it }
                val tri = when (p.optInt("mode", 4)) {
                    4 -> raw
                    5 -> IntArray(max(0, raw.size - 2) * 3).also { o ->
                        for (i in 0 until raw.size - 2) {
                            val even = i % 2 == 0
                            o[i * 3] = raw[if (even) i else i + 1]; o[i * 3 + 1] = raw[if (even) i + 1 else i]; o[i * 3 + 2] = raw[i + 2]
                        }
                    }
                    6 -> IntArray(max(0, raw.size - 2) * 3).also { o ->
                        for (i in 0 until raw.size - 2) { o[i * 3] = raw[0]; o[i * 3 + 1] = raw[i + 1]; o[i * 3 + 2] = raw[i + 2] }
                    }
                    else -> return@mapNotNull null
                }
                val valid = tri.size - tri.size % 3
                for (k in 0 until valid) if (tri[k] !in 0 until vertexCount) throw ImportException("Indeks mesh di luar jangkauan; file mungkin rusak.")
                Prim(pos, if (valid == tri.size) tri else tri.copyOf(valid), materialColors.getOrNull(p.optInt("material", -1)))
            }
        }

        val nodes = root.optJSONArray("nodes").objects()
        val out = ArrayList<RawPart>()
        fun visit(nodeIndex: Int, parent: FloatArray, depth: Int) {
            val node = nodes.getOrNull(nodeIndex) ?: return
            if (depth > 64) return
            val world = Mat4.mul(parent, Mat4.local(node))
            if (node.has("mesh")) {
                meshes.getOrNull(node.getInt("mesh"))?.forEach { prim ->
                    out += RawPart(Mat4.transformPoints(world, prim.positions), prim.indices, prim.color)
                }
            }
            node.optJSONArray("children")?.let { ch -> for (i in 0 until ch.length()) visit(ch.getInt(i), world, depth + 1) }
        }
        val scenes = root.optJSONArray("scenes").objects()
        val sceneNodes = scenes.getOrNull(root.optInt("scene", 0))?.optJSONArray("nodes")
        when {
            sceneNodes != null -> for (i in 0 until sceneNodes.length()) visit(sceneNodes.getInt(i), Mat4.identity(), 0)
            nodes.isNotEmpty() -> {
                val children = nodes.flatMap { n -> n.optJSONArray("children")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList() }.toSet()
                nodes.indices.filter { it !in children }.forEach { visit(it, Mat4.identity(), 0) }
            }
            else -> meshes.flatten().forEach { out += RawPart(it.positions, it.indices, it.color) }
        }
        return out
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}

private fun linearToSrgb(v: Float) = v.coerceIn(0f, 1f).toDouble().pow(1 / 2.2).toFloat()

/** Column-major 4×4 helpers for baking the glTF node hierarchy. */
private object Mat4 {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    fun mul(a: FloatArray, b: FloatArray) = FloatArray(16) { i ->
        val col = i / 4; val row = i % 4
        a[row] * b[col * 4] + a[4 + row] * b[col * 4 + 1] + a[8 + row] * b[col * 4 + 2] + a[12 + row] * b[col * 4 + 3]
    }

    fun local(node: JSONObject): FloatArray {
        node.optJSONArray("matrix")?.let { m -> if (m.length() == 16) return FloatArray(16) { m.getDouble(it).toFloat() } }
        val t = node.optJSONArray("translation")
        val r = node.optJSONArray("rotation")
        val s = node.optJSONArray("scale")
        val x = r?.optDouble(0, 0.0)?.toFloat() ?: 0f; val y = r?.optDouble(1, 0.0)?.toFloat() ?: 0f
        val z = r?.optDouble(2, 0.0)?.toFloat() ?: 0f; val w = r?.optDouble(3, 1.0)?.toFloat() ?: 1f
        val sx = s?.optDouble(0, 1.0)?.toFloat() ?: 1f; val sy = s?.optDouble(1, 1.0)?.toFloat() ?: 1f; val sz = s?.optDouble(2, 1.0)?.toFloat() ?: 1f
        return floatArrayOf(
            (1 - 2 * (y * y + z * z)) * sx, (2 * (x * y + w * z)) * sx, (2 * (x * z - w * y)) * sx, 0f,
            (2 * (x * y - w * z)) * sy, (1 - 2 * (x * x + z * z)) * sy, (2 * (y * z + w * x)) * sy, 0f,
            (2 * (x * z + w * y)) * sz, (2 * (y * z - w * x)) * sz, (1 - 2 * (x * x + y * y)) * sz, 0f,
            t?.optDouble(0, 0.0)?.toFloat() ?: 0f, t?.optDouble(1, 0.0)?.toFloat() ?: 0f, t?.optDouble(2, 0.0)?.toFloat() ?: 0f, 1f,
        )
    }

    fun transformPoints(m: FloatArray, p: FloatArray) = FloatArray(p.size).also { o ->
        for (i in p.indices step 3) {
            val x = p[i]; val y = p[i + 1]; val z = p[i + 2]
            o[i] = m[0] * x + m[4] * y + m[8] * z + m[12]
            o[i + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
            o[i + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
        }
    }
}

/** Average colour of an embedded texture, so textured models keep their overall look. */
private fun averageImageColor(bytes: ByteArray): Color? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 32 && bounds.outHeight / (sample * 2) >= 32) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    bmp.recycle()
    var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
    for (c in px) {
        val a = (c ushr 24) / 255.0
        r += ((c shr 16) and 0xFF) * a; g += ((c shr 8) and 0xFF) * a; b += (c and 0xFF) * a; w += a
    }
    if (w < 1e-6) return null
    return Color((r / w / 255).toFloat(), (g / w / 255).toFloat(), (b / w / 255).toFloat())
}

// ---------------------------------------------------------------- Wavefront OBJ + MTL

private object Obj {
    fun parse(text: String, siblings: Map<String, SourceFile>): List<RawPart> {
        val pos = FloatBuilder()
        val groups = LinkedHashMap<String, IntBuilder>()
        val colors = HashMap<String, Color>()
        var current = groups.getOrPut("") { IntBuilder() }
        val face = IntBuilder()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.length < 2 || line[0] == '#') return@forEach
            val tk = StringTokenizer(line)
            when (tk.nextToken()) {
                "v" -> {
                    pos.add(tk.nextToken().toFloatOrNull() ?: 0f); pos.add(tk.nextToken().toFloatOrNull() ?: 0f); pos.add(tk.nextToken().toFloatOrNull() ?: 0f)
                }
                "f" -> {
                    face.clear()
                    val vCount = pos.size / 3
                    while (tk.hasMoreTokens()) {
                        val idx = tk.nextToken().substringBefore('/').toIntOrNull() ?: continue
                        val v = if (idx < 0) vCount + idx else idx - 1
                        if (v in 0 until vCount) face.add(v)
                    }
                    for (k in 1 until face.size - 1) { current.add(face.data[0]); current.add(face.data[k]); current.add(face.data[k + 1]) }
                }
                "usemtl" -> current = groups.getOrPut(if (tk.hasMoreTokens()) tk.nextToken() else "") { IntBuilder() }
                "mtllib" -> while (tk.hasMoreTokens()) {
                    siblings[baseName(tk.nextToken())]?.let { parseMtl(String(it.bytes, Charsets.UTF_8), colors, siblings) }
                }
            }
        }
        val positions = pos.toArray()
        return groups.mapNotNull { (material, tris) ->
            if (tris.size == 0) return@mapNotNull null
            compact(positions, tris.toArray(), colors[material])
        }
    }

    private fun parseMtl(text: String, colors: MutableMap<String, Color>, siblings: Map<String, SourceFile>) {
        var name = ""
        text.lineSequence().forEach { raw ->
            val tk = StringTokenizer(raw.trim())
            if (!tk.hasMoreTokens()) return@forEach
            when (tk.nextToken()) {
                "newmtl" -> name = if (tk.hasMoreTokens()) tk.nextToken() else ""
                "Kd" -> runCatching {
                    colors[name] = Color(tk.nextToken().toFloat().coerceIn(0f, 1f), tk.nextToken().toFloat().coerceIn(0f, 1f), tk.nextToken().toFloat().coerceIn(0f, 1f))
                }
                "map_Kd" -> {
                    var file = ""
                    while (tk.hasMoreTokens()) file = tk.nextToken()
                    siblings[baseName(file)]?.let { averageImageColor(it.bytes) }?.let { colors[name] = it }
                }
            }
        }
    }
}

/** Builds a part holding only the vertices its triangles use. */
private fun compact(positions: FloatArray, tris: IntArray, color: Color?): RawPart {
    val map = HashMap<Int, Int>()
    val pos = FloatBuilder()
    val idx = IntArray(tris.size) { i ->
        map.getOrPut(tris[i]) {
            val v = tris[i]
            pos.add(positions[v * 3]); pos.add(positions[v * 3 + 1]); pos.add(positions[v * 3 + 2])
            pos.size / 3 - 1
        }
    }
    return RawPart(pos.toArray(), idx, color)
}

// ---------------------------------------------------------------- STL (binary + ASCII)

private object Stl {
    fun parse(bytes: ByteArray): List<RawPart> {
        val binaryCount = if (bytes.size >= 84) ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL else -1L
        val isBinary = binaryCount >= 0 && 84 + binaryCount * 50 == bytes.size.toLong()
        val pos = FloatBuilder()
        if (isBinary) {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (t in 0 until binaryCount.toInt()) {
                val at = 84 + t * 50 + 12
                for (k in 0 until 9) pos.add(bb.getFloat(at + k * 4))
            }
        } else {
            val tk = StringTokenizer(String(bytes, Charsets.US_ASCII))
            while (tk.hasMoreTokens()) {
                if (tk.nextToken() == "vertex") repeat(3) { pos.add(tk.nextToken().toFloatOrNull() ?: 0f) }
            }
        }
        val positions = pos.toArray()
        return listOf(RawPart(positions, IntArray(positions.size / 3) { it }, null))
    }
}

// ---------------------------------------------------------------- PLY (ASCII + binary)

private object Ply {
    private class Property(val name: String, val type: String, val listCount: String?)
    private class Element(val name: String, val count: Int, val props: List<Property>)

    fun parse(bytes: ByteArray): List<RawPart> {
        val headerEnd = indexOf(bytes, "end_header".toByteArray())
        if (headerEnd < 0 || !String(bytes, 0, 3, Charsets.US_ASCII).startsWith("ply")) throw ImportException("Bukan file PLY yang valid.")
        var bodyStart = headerEnd + "end_header".length
        while (bodyStart < bytes.size && bytes[bodyStart] != '\n'.code.toByte()) bodyStart++
        bodyStart++
        var format = "ascii"
        val elements = ArrayList<Element>()
        var props = ArrayList<Property>()
        var pending: Pair<String, Int>? = null
        fun flush() { pending?.let { elements += Element(it.first, it.second, props) }; props = ArrayList() }
        String(bytes, 0, headerEnd, Charsets.US_ASCII).lines().forEach { line ->
            val t = line.trim().split(Regex("\\s+"))
            when (t.firstOrNull()) {
                "format" -> format = t.getOrElse(1) { "ascii" }
                "element" -> { flush(); pending = t[1] to t[2].toInt() }
                "property" -> props += if (t[1] == "list") Property(t[4], t[3], t[2]) else Property(t[2], t[1], null)
            }
        }
        flush()

        val pos = FloatBuilder()
        val tris = IntBuilder()
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0; var cCount = 0
        val reader: Reader = if (format == "ascii") AsciiReader(String(bytes, bodyStart, bytes.size - bodyStart, Charsets.US_ASCII))
        else BinaryReader(ByteBuffer.wrap(bytes, bodyStart, bytes.size - bodyStart).slice().order(if (format == "binary_big_endian") ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN))
        val poly = IntBuilder()
        for (e in elements) {
            for (i in 0 until e.count) {
                var x = 0f; var y = 0f; var z = 0f; var r = -1.0; var g = 0.0; var b = 0.0
                poly.clear()
                for (p in e.props) {
                    if (p.listCount != null) {
                        val n = reader.read(p.listCount).toInt()
                        repeat(n) { poly.add(reader.read(p.type).toInt()) }
                    } else {
                        val v = reader.read(p.type)
                        when (p.name) {
                            "x" -> x = v.toFloat(); "y" -> y = v.toFloat(); "z" -> z = v.toFloat()
                            "red", "diffuse_red" -> r = v; "green", "diffuse_green" -> g = v; "blue", "diffuse_blue" -> b = v
                        }
                    }
                }
                when (e.name) {
                    "vertex" -> {
                        pos.add(x); pos.add(y); pos.add(z)
                        if (r >= 0) { rSum += r; gSum += g; bSum += b; cCount++ }
                    }
                    "face" -> for (k in 1 until poly.size - 1) { tris.add(poly.data[0]); tris.add(poly.data[k]); tris.add(poly.data[k + 1]) }
                }
            }
        }
        val vCount = pos.size / 3
        val idx = tris.toArray().filter { it in 0 until vCount }.toIntArray()
        if (idx.isEmpty()) throw ImportException("PLY ini berupa point cloud tanpa face; belum bisa ditampilkan.")
        val color = if (cCount > 0) Color((rSum / cCount / 255).toFloat().coerceIn(0f, 1f), (gSum / cCount / 255).toFloat().coerceIn(0f, 1f), (bSum / cCount / 255).toFloat().coerceIn(0f, 1f)) else null
        return listOf(RawPart(pos.toArray(), idx.copyOf(idx.size - idx.size % 3), color))
    }

    private interface Reader { fun read(type: String): Double }

    private class AsciiReader(text: String) : Reader {
        private val tk = StringTokenizer(text)
        override fun read(type: String) = if (tk.hasMoreTokens()) tk.nextToken().toDoubleOrNull() ?: 0.0 else 0.0
    }

    private class BinaryReader(private val bb: ByteBuffer) : Reader {
        override fun read(type: String): Double = when (type) {
            "char", "int8" -> bb.get().toDouble()
            "uchar", "uint8" -> (bb.get().toInt() and 0xFF).toDouble()
            "short", "int16" -> bb.getShort().toDouble()
            "ushort", "uint16" -> (bb.getShort().toInt() and 0xFFFF).toDouble()
            "int", "int32" -> bb.getInt().toDouble()
            "uint", "uint32" -> (bb.getInt().toLong() and 0xFFFFFFFFL).toDouble()
            "float", "float32" -> bb.getFloat().toDouble()
            "double", "float64" -> bb.getDouble()
            else -> throw ImportException("Tipe properti PLY \"$type\" tidak dikenal.")
        }
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}

// ---------------------------------------------------------------- Growable primitive arrays

internal class FloatBuilder {
    var data = FloatArray(1024); private set
    var size = 0; private set
    fun add(v: Float) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
    fun toArray() = data.copyOf(size)
}

internal class IntBuilder {
    var data = IntArray(1024); private set
    var size = 0; private set
    fun add(v: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
    fun clear() { size = 0 }
    fun toArray() = data.copyOf(size)
}

// ---------------------------------------------------------------- Library of imported models

/** Imported models, persisted in app storage in a compact binary form so they load instantly. */
object ImportedModels {
    val models = mutableStateListOf<LibraryModel>()
    private var loaded = false
    private const val MAGIC = 0x474D4431 // "GMD1"

    private fun dir(context: Context) = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun load(context: Context) {
        if (loaded) return
        loaded = true
        val list = withContext(Dispatchers.IO) {
            dir(context).listFiles { f -> f.extension == "gmdl" }.orEmpty().sortedBy { it.lastModified() }
                .mapNotNull { f -> runCatching { read(f) }.getOrNull() }
        }
        models.addAll(list.filter { m -> models.none { it.id == m.id } })
    }

    /** Reads the picked files, converts them, stores the result and adds it to the library. */
    suspend fun import(context: Context, uris: List<Uri>): ModelImporter.Result {
        val files = withContext(Dispatchers.IO) {
            uris.map { uri ->
                val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else -1L
                } ?: -1L
                if (size > 200L * 1024 * 1024) throw ImportException("File lebih dari 200 MB. Kecilkan dulu di Blender.")
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ImportException("File tidak bisa dibaca.")
                SourceFile(displayName(context, uri), bytes)
            }
        }
        val id = "imp-" + java.lang.Long.toString(System.currentTimeMillis(), 36)
        val result = withContext(Dispatchers.Default) { ModelImporter.import(files, id) }
        withContext(Dispatchers.IO) { write(File(dir(context), "$id.gmdl"), result.model) }
        models += result.model
        return result
    }

    suspend fun delete(context: Context, id: String) {
        withContext(Dispatchers.IO) { File(dir(context), "$id.gmdl").delete() }
        models.removeAll { it.id == id }
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "model"

    private fun write(file: File, m: LibraryModel) {
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { o ->
            o.writeInt(MAGIC); o.writeUTF(m.id); o.writeUTF(m.name); o.writeInt(m.color.toArgbInt()); o.writeFloat(m.restHeight)
            o.writeInt(m.parts.size)
            for (p in m.parts) {
                o.writeBoolean(p.color != null); o.writeInt(p.color?.toArgbInt() ?: 0)
                o.writeInt(p.mesh.vertices.size)
                for (v in p.mesh.vertices) { o.writeFloat(v.x); o.writeFloat(v.y); o.writeFloat(v.z) }
                o.writeInt(p.mesh.faces.size)
                for (f in p.mesh.faces) { o.writeInt(f[0]); o.writeInt(f[1]); o.writeInt(f[2]) }
            }
        }
    }

    private fun read(file: File): LibraryModel = DataInputStream(BufferedInputStream(file.inputStream())).use { i ->
        if (i.readInt() != MAGIC) error("not a model file")
        val id = i.readUTF(); val name = i.readUTF(); val color = Color(i.readInt()); val rest = i.readFloat()
        val parts = List(i.readInt()) {
            val hasColor = i.readBoolean(); val argb = i.readInt()
            val verts = List(i.readInt()) { Vec3(i.readFloat(), i.readFloat(), i.readFloat()) }
            val faces = List(i.readInt()) { intArrayOf(i.readInt(), i.readInt(), i.readInt()) }
            Part(Mesh(verts, faces, doubleSided = true), color = if (hasColor) Color(argb) else null)
        }
        LibraryModel(id, name, ModelCategory.Imported, color, parts, restHeight = rest)
    }

    private fun Color.toArgbInt(): Int =
        ((alpha * 255 + 0.5f).toInt() shl 24) or ((red * 255 + 0.5f).toInt() shl 16) or ((green * 255 + 0.5f).toInt() shl 8) or (blue * 255 + 0.5f).toInt()
}
