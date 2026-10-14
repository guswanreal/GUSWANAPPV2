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
    private fun open() {
        android.util.Log.i("EditorUiQa","Creating editor content")
        compose.mainClock.autoAdvance=true
        val settings=ProjectSettings(name="Motion study",duration=6f)
        val shape=Layer(1,"Shape 1",LayerType.Shape,Color(0xFFFF6A2B),0f,6f)
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
        compose.onNodeWithText("Overshoot").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Fit keys").performScrollTo()
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
}

