package com.example.myapplication

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.engine.*
import com.example.myapplication.ui.editor.VideoEditorScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditorWorkspaceUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(layerOverride:Layer?=null) {
        android.util.Log.i("EditorUiQa","Creating editor content")
        compose.mainClock.autoAdvance=true
        val settings=ProjectSettings(name="Motion study",duration=6f)
        val shape=layerOverride ?: Layer(1,"Shape 1",LayerType.Shape,Color(0xFFFF6A2B),0f,6f)
            .withTrack(Prop.PosX,Track(0f,listOf(Keyframe(0f,-.5f),Keyframe(2f,.5f))))
        compose.setContent { MyApplicationTheme { VideoEditorScreen(settings,null,onBack={},restored=ProjectSnapshot("qa-editor",settings,listOf(shape),selectedId=1),autosaveEnabled=false) } }
        compose.mainClock.advanceTimeBy(1000)
        android.util.Log.i("EditorUiQa","Editor content ready")
    }
    private fun capture(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val orientation=if(bitmap.width>bitmap.height) "landscape" else "portrait"
        val scale=context.resources.configuration.fontScale
        val directory=File(context.filesDir,"qa").apply { mkdirs() }
        File(directory,"$name-$orientation-${if(scale>1.5f) "large-type" else "default"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test(timeout=120000) fun editorKeepsTimelineContextAndOpensFlowControls() {
        open()
        compose.onNodeWithTag("editor-workspace").assertIsDisplayed()
        capture("editor")
        compose.onNodeWithText("Graph").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("curve-controls").assertExists()
        FlowCurves.forEach { curve ->
            compose.onNodeWithText(curve.name).performScrollTo().performClick()
            android.util.Log.i("EditorUiQa","Applied curve ${curve.name}")
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Speed Graph").performScrollTo().performClick()
        compose.onNodeWithText("Value Graph").performClick()
        compose.onNodeWithText("Fit keys").performClick()
        compose.onNodeWithText("Full comp").performClick()
        compose.onNodeWithText("Semua segmen").performScrollTo().performClick()
        compose.onNodeWithText("Segmen 1").performClick()
        compose.onNodeWithText("Key 1").performScrollTo().performClick()
        compose.onNodeWithText("Key 2:",substring=true).performClick()
        compose.onNodeWithTag("scrub-Out influence").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("45")
        compose.onNodeWithText("Terapkan").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("graph-canvas").performScrollTo().assertIsDisplayed()
        capture("graph")
        compose.onNodeWithContentDescription("Tutup panel").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("curve-controls").assertDoesNotExist()
    }

    @Test(timeout=120000) fun numericControlsRejectInvalidValuesAndEffectPresetsAreReachable() {
        open()
        compose.onNodeWithText("Transform").performClick()
        android.util.Log.i("EditorUiQa","Transform opened")
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("scrub-Position X").performScrollTo().performClick()
        android.util.Log.i("EditorUiQa","Numeric dialog opened")
        compose.mainClock.advanceTimeBy(1000)
        compose.onNode(hasSetTextAction()).performTextReplacement("NaN")
        compose.onNodeWithText("Masukkan angka yang valid.").assertIsDisplayed()
        compose.onNodeWithText("Terapkan").assertIsNotEnabled()
        compose.onNodeWithText("Batal").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Efek").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Tambah efek").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Preset stack").performClick()
        compose.onNodeWithText("RGB hit").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("RGB Split").assertExists()
    }

    @Test(timeout=120000) fun cameraRigButtonCreatesANullAndItsEffectsAreReachable() {
        open(Layer(1,"Camera 1",LayerType.Camera,Color(0xFFFF6A2B),0f,6f))
        compose.onNodeWithText("Transform").performClick()
        compose.onNodeWithText("Buat Null pengendali kamera").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Mode 3D").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Efek").performClick()
        compose.onNodeWithText("Tambah efek").performScrollTo().performClick()
        compose.onNodeWithText("Preset stack").performClick()
        compose.onNodeWithText("Handheld rig").performScrollTo().performClick()
        compose.onNodeWithText("Shake").assertExists()
        compose.onNodeWithContentDescription("Pilih layer").performClick()
        compose.onNodeWithText("Camera 1").performClick()
        compose.onNodeWithText("Parent").performClick()
        compose.onNodeWithText("Tidak ada").performScrollTo().performClick()
        android.util.Log.i("EditorUiQa","Camera rig created, effect applied, camera detached")
    }

    @Test(timeout=120000) fun faceTrackingRouteShowsAnErrorForBlankFootage() {
        val directory=File(context.cacheDir,"blank-ui-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val bitmap=Bitmap.createBitmap(480,360,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        try {
            for(i in 0..5) File(directory,"f%05d.jpg".format(i)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }
            val footage=Footage("blank-ui","file:///test",false,480,360,.5f,10f,6,directory.path)
            open(Layer(1,"Video 1",LayerType.Video,Color.White,0f,.5f,footage=footage))
            compose.onNodeWithText("Tracking").performClick()
            compose.onNodeWithTag("face-tracking-panel").assertExists()
            compose.onNodeWithText("Camera solve").performScrollTo().performClick()
            compose.onNodeWithText("Tracking kamera 3D").assertExists()
            compose.onNodeWithText("Face 3D").performScrollTo().performClick()
            compose.onNodeWithTag("start-face-tracking").performScrollTo().performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithText("Wajah belum terlacak.",substring=true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Wajah belum terlacak.",substring=true).performScrollTo().assertIsDisplayed()
            android.util.Log.i("EditorUiQa","Face analysis rejected blank footage with a visible error")
        } finally { bitmap.recycle(); directory.deleteRecursively() }
    }
}

