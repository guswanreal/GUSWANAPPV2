package com.example.myapplication.engine

import android.graphics.Matrix
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix as ComposeMatrix

/** Affine compensation in normalized (x / width, y / height) composition space. */
data class ParentOffset(
    val a: Float = 1f, val b: Float = 0f,
    val c: Float = 0f, val d: Float = 1f,
    val x: Float = 0f, val y: Float = 0f,
) {
    internal fun pixels(size: Size) = Matrix().apply {
        setValues(floatArrayOf(
            a, c * size.width / size.height, x * size.width,
            b * size.height / size.width, d, y * size.height,
            0f, 0f, 1f,
        ))
    }

    companion object {
        internal fun fromPixels(matrix: Matrix, size: Size): ParentOffset {
            val v = FloatArray(9)
            matrix.getValues(v)
            require(v.all { it.isFinite() }) { "Transformasi parent tidak valid." }
            return ParentOffset(v[0], v[3] * size.width / size.height,
                v[1] * size.height / size.width, v[4], v[2] / size.width, v[5] / size.height)
        }
    }
}

/**
 * One evaluator per frame/sample time. World and parent matrices are memoized per layer.
 * The iterative walk supports deep trees without recursion or a fixed depth limit.
 * Parent opacity, visibility and in/out points do not affect inherited transforms.
 */
class LayerHierarchy(
    private val byId: Map<Int, Layer>,
    private val time: Float,
    private val size: Size,
) {
    private class Entry(val basis: Matrix, val world: Matrix)
    private val cache = HashMap<Int, Entry>()

    init {
        require(size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0)
    }

    private fun resolve(layer: Layer): Entry {
        cache[layer.id]?.let { return it }
        val pending = ArrayList<Layer>()
        val seen = HashSet<Int>()
        var cursor: Layer? = layer
        var ancestor: Entry? = null
        while (cursor != null) {
            val cached = cache[cursor.id]
            if (cached != null) { ancestor = cached; break }
            // Malformed imported cycles terminate here; editor mutations reject them.
            if (!seen.add(cursor.id)) break
            pending.add(cursor)
            cursor = cursor.parentId?.let(byId::get)
        }
        for (i in pending.lastIndex downTo 0) {
            val current = pending[i]
            val basis = Matrix()
            if (ancestor != null) {
                basis.set(ancestor.world)
                // Existing projects store child positions relative to composition centre.
                basis.preTranslate(-size.width / 2f, -size.height / 2f)
            }
            basis.preConcat(current.parentOffset.pixels(size))
            val position = layerPosition(current, time, size)
            val anchor = anchorOffset(current, time, size)
            val scale = layerScale(current, time)
            val local = Matrix().apply {
                setTranslate(position.x, position.y)
                preRotate(planarRotation(current, time))
                preScale(scale, scale)
                preTranslate(-anchor.x, -anchor.y)
            }
            val entry = Entry(basis, Matrix().apply { setConcat(basis, local) })
            cache[current.id] = entry
            ancestor = entry
        }
        return cache.getValue(layer.id)
    }

    fun world(layer: Layer, out: Matrix) { out.set(resolve(layer).world) }
    fun parentBasis(layer: Layer, out: Matrix) { out.set(resolve(layer).basis) }

    internal fun worldCompose(layer: Layer): ComposeMatrix = resolve(layer).world.toCompose()
    internal fun parentCompose(layer: Layer): ComposeMatrix = resolve(layer).basis.toCompose()

    internal fun childSpace(parent: Layer?, out: Matrix) {
        if (parent == null) out.reset() else {
            world(parent, out)
            out.preTranslate(-size.width / 2f, -size.height / 2f)
        }
    }
}

/** Keep Android's row-major affine coefficients separate from Compose's 4x4 storage. */
private fun Matrix.toCompose(): ComposeMatrix {
    val v = FloatArray(9)
    getValues(v)
    return ComposeMatrix().apply {
        values[0] = v[0]; values[1] = v[3]
        values[4] = v[1]; values[5] = v[4]
        values[12] = v[2]; values[13] = v[5]
    }
}

/** Reject missing parents, unsupported layer types and cycles at any depth. */
fun canParentLayer(layers: List<Layer>, childId: Int, parentId: Int?): Boolean {
    val byId = layers.associateBy { it.id }
    val child = byId[childId] ?: return false
    if (parentId == null) return true
    if (child.type == LayerType.Gradient) return false
    if (child.type == LayerType.Camera && byId[parentId]?.type != LayerType.Null) return false
    val seen = hashSetOf(childId)
    var next: Int? = parentId
    while (next != null) {
        if (!seen.add(next)) return false
        val parent = byId[next] ?: return false
        if (parent.type == LayerType.Gradient || parent.type == LayerType.Camera) return false
        next = parent.parentId
    }
    return true
}

/**
 * Preserve the visual transform at [time] without rewriting keyframes or decomposing shear.
 * newOffset = inverse(newParentSpace) * oldParentBasis.
 * Baking the old parent's entire animated path is a separate operation.
 */
fun reparentLayer(
    layers: List<Layer>, childId: Int, parentId: Int?, time: Float, size: Size,
    keepWorld: Boolean = true,
): Layer {
    require(canParentLayer(layers, childId, parentId)) { "Parent tidak valid atau membentuk siklus." }
    val byId = layers.associateBy { it.id }
    val child = byId.getValue(childId)
    if (child.parentId == parentId) return child
    val parent = parentId?.let(byId::get)
    if (child.type == LayerType.Camera || child.is3D || parent?.is3D == true) {
        require(child.supports3D()) { "Layer ini belum mendukung parenting 3D." }
        return if (keepWorld) Scene3D(layers,time,size).reparent(child,parent)
        else child.copy(parentId=parentId, parentOffset=ParentOffset(), spatialOffset=emptyList(), is3D=true)
    }
    if (!keepWorld) return child.copy(parentId = parentId, parentOffset = ParentOffset())
    val hierarchy = LayerHierarchy(byId, time, size)
    val oldBasis = Matrix().also { hierarchy.parentBasis(child, it) }
    val newSpace = Matrix().also { hierarchy.childSpace(parentId?.let(byId::get), it) }
    val inverse = Matrix()
    require(newSpace.invert(inverse)) { "Parent memiliki Scale 0; ubah skalanya sebelum menghubungkan layer." }
    val offset = Matrix().apply { setConcat(inverse, oldBasis) }
    return child.copy(parentId = parentId, parentOffset = ParentOffset.fromPixels(offset, size))
}
