package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.engine.*
import com.example.myapplication.ui.editor.EditorState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerHierarchyTest {
    private val size = Size(1000f, 500f)
    private fun layer(id: Int, type: LayerType = LayerType.Null, parent: Int? = null) =
        Layer(id, "Layer $id", type, Color.Red, 0f, 10f, parentId = parent)

    private fun mapped(layers: List<Layer>, id: Int, size: Size = this.size, t: Float = 0f): FloatArray {
        val matrix = Matrix()
        LayerHierarchy(layers.associateBy { it.id }, t, size).world(layers.first { it.id == id }, matrix)
        return floatArrayOf(0f, 0f, size.width * .1f, 0f, 0f, size.height * .1f).also(matrix::mapPoints)
    }

    @Test fun positionRotationScaleAndBothAnchorsComposeInOrder() {
        val parent = layer(1).withTrack(Prop.PosX, Track(.2f))
            .withTrack(Prop.AnchorX, Track(.1f))
            .withTrack(Prop.Rotation, Track(90f)).withTrack(Prop.Scale, Track(2f))
        val child = layer(2, LayerType.Shape, 1).withTrack(Prop.PosX, Track(.4f))
            .withTrack(Prop.AnchorY, Track(.1f))
        val values = mapped(listOf(parent, child), 2)
        assertEquals(650f, values[0], .001f)
        assertEquals(550f, values[1], .001f)
        assertEquals(650f, values[2], .001f)
        assertEquals(750f, values[3], .001f)
        assertEquals(550f, values[4], .001f)
        assertEquals(550f, values[5], .001f)
        val compose = layerMatrix(child, mapOf(1 to parent, 2 to child), 0f, size)
        assertEquals(Offset(650f, 550f), compose.map(Offset.Zero))
    }

    @Test fun hierarchyBeyondEightLevelsAndCycleValidation() {
        val layers = (1..40).map {
            layer(it, parent = if (it == 1) null else it - 1).withTrack(Prop.PosX, Track(.02f))
        }
        assertEquals(900f, mapped(layers, 40)[0], .01f)
        assertFalse(canParentLayer(layers, 1, 40))
        assertFalse(canParentLayer(layers, 1, 1))
        assertFalse(canParentLayer(layers, 1, 999))
        assertTrue(canParentLayer(layers, 40, null))
    }

    @Test fun reparentAndDetachPreserveAllCornersAndAnimationTracks() {
        val a = layer(1).withTrack(Prop.Rotation, Track(33f)).withTrack(Prop.Scale, Track(1.7f))
        val b = layer(2).withTrack(Prop.PosX, Track(.3f)).withTrack(Prop.Rotation, Track(-71f))
            .withTrack(Prop.AnchorY, Track(.2f))
        val child = layer(3, LayerType.Shape, 1).withTrack(Prop.PosX,
            Track(0f, listOf(Keyframe(0f, -.4f), Keyframe(10f, .4f))))
        val old = listOf(a, b, child)
        val changed = reparentLayer(old, 3, 2, 4f, size)
        val next = listOf(a, b, changed)
        assertEquals(child.tracks, changed.tracks)
        for (t in listOf(0f, 4f, 10f)) {
            assertArrayEquals(mapped(old, 3, t = t), mapped(next, 3, t = t), .001f)
        }
        val detached = reparentLayer(next, 3, null, 4f, size)
        assertArrayEquals(mapped(next, 3, t = 4f), mapped(listOf(a, b, detached), 3, t = 4f), .001f)
    }

    @Test fun compensationMatchesPreviewAndExportSizes() {
        val parent = layer(1).withTrack(Prop.Rotation, Track(45f)).withTrack(Prop.PosY, Track(.5f))
        val child = layer(2, LayerType.Shape).withTrack(Prop.AnchorX, Track(.3f))
        val changed = reparentLayer(listOf(parent, child), 2, 1, 0f, size)
        val small = mapped(listOf(parent, changed), 2)
        val large = mapped(listOf(parent, changed), 2, Size(2000f, 1000f))
        assertArrayEquals(small, large.map { it / 2f }.toFloatArray(), .001f)
    }

    @Test fun singularParentIsRejectedButSingularChildCanBeReparented() {
        val parent = layer(1).withTrack(Prop.Scale, Track(0f))
        val child = layer(2, LayerType.Shape)
        assertThrows(IllegalArgumentException::class.java) {
            reparentLayer(listOf(parent, child), 2, 1, 0f, size)
        }
        val zeroChild = child.withTrack(Prop.Scale, Track(0f))
        val normalParent = layer(1).withTrack(Prop.Rotation, Track(90f))
        val changed = reparentLayer(listOf(normalParent, zeroChild), 2, 1, 0f, size)
        assertArrayEquals(mapped(listOf(zeroChild), 2), mapped(listOf(normalParent, changed), 2), .001f)
        assertEquals(Offset.Zero, screenDeltaToParentSpace(
            listOf(parent, child.copy(parentId = 1)), 2, 0f, size, Offset(20f, 30f)))
    }

    @Test fun dragRespectsRotatedScaledParentAndCompensation() {
        val parent = layer(1).withTrack(Prop.Rotation, Track(90f)).withTrack(Prop.Scale, Track(2f))
        val child = layer(2, LayerType.Shape, 1)
        val delta = screenDeltaToParentSpace(listOf(parent, child), 2, 0f, size, Offset(0f, 20f))
        assertEquals(10f, delta.x, .001f)
        assertEquals(0f, delta.y, .001f)
        val detached = reparentLayer(listOf(parent, child), 2, null, 0f, size)
        val after = screenDeltaToParentSpace(listOf(detached), 2, 0f, size, Offset(0f, 20f))
        assertEquals(delta.x, after.x, .001f)
        assertEquals(delta.y, after.y, .001f)
    }

    @Test fun editorDeleteUndoRedoPreserveDescendants() {
        val state = EditorState(ProjectSettings(), null)
        val projectSize = Size(state.settings.width.toFloat(), state.settings.height.toFloat())
        val parent = layer(1).withTrack(Prop.Rotation, Track(57f)).withTrack(Prop.Scale, Track(1.4f))
        state.commit(listOf(parent, layer(2, parent = 1), layer(3, LayerType.Shape, 2)))
        val before = mapped(state.layers, 3, projectSize)
        state.select(1)
        state.deleteSelected()
        assertNull(state.layers.first { it.id == 2 }.parentId)
        assertArrayEquals(before, mapped(state.layers, 3, projectSize), .001f)
        state.undo()
        assertEquals(1, state.layers.first { it.id == 2 }.parentId)
        assertArrayEquals(before, mapped(state.layers, 3, projectSize), .001f)
        state.redo()
        assertArrayEquals(before, mapped(state.layers, 3, projectSize), .001f)
    }

    @Test fun editorRejectsCyclesAndZeroScaleWithoutChangingState() {
        val state = EditorState(ProjectSettings(), null)
        state.commit(listOf(layer(1), layer(2, parent = 1), layer(3).withTrack(Prop.Scale, Track(0f))))
        val before = state.layers
        state.select(1)
        state.setParent(2)
        assertNotNull(state.parentError)
        assertEquals(before, state.layers)
        state.setParent(3)
        assertNotNull(state.parentError)
        assertEquals(before, state.layers)
    }

    @Test fun malformedImportedCycleTerminates() {
        val values = mapped(listOf(layer(1, parent = 2), layer(2, parent = 1)), 1)
        assertTrue(values.all { it.isFinite() })
    }

    private fun render(layers: List<Layer>): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(200f, 100f)) {
            drawComposition(layers, 0f, measurer, background = Color.Black)
        }
        return bitmap
    }

    @Test fun nullIsInvisibleInOutputAndDoesNotHideItsChild() {
        val nullLayer = layer(1).copy(solo = true)
        val empty = render(emptyList())
        val onlyNull = render(listOf(nullLayer))
        try { assertTrue(empty.sameAs(onlyNull)) } finally { empty.recycle(); onlyNull.recycle() }
        val hiddenNull = nullLayer.copy(visible = false, end = -1f)
            .withTrack(Prop.Opacity, Track(0f))
        val child = layer(2, LayerType.Shape, 1).copy(shape = ShapeKind.Rect)
        val image = render(listOf(hiddenNull, child))
        try { assertEquals(android.graphics.Color.RED, image.getPixel(100, 50)) } finally { image.recycle() }
    }
}
