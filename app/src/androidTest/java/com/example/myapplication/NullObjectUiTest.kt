package com.example.myapplication

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.engine.ProjectSettings
import com.example.myapplication.ui.editor.VideoEditorScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NullObjectUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addNullFromEditorAndOpenParentPanel() {
        // The add menu contains continuously animated model/particle previews.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MyApplicationTheme { VideoEditorScreen(ProjectSettings(), null, onBack = {}, autosaveEnabled = false) }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Tambah layer").performClick()
        compose.mainClock.advanceTimeBy(1000)
        // Media card, then Text/Shape/Model/Gradient/Particles/Camera/Null.
        compose.onNodeWithTag("add-layer-grid").performScrollToIndex(7)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Null Object").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodesWithText("Null 1").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Parent").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Tidak ada").assertIsDisplayed()
    }

    @Test fun generateOfflineMotionFromEditor() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MyApplicationTheme { VideoEditorScreen(ProjectSettings(), null, onBack = {}, autosaveEnabled = false) }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Buat Motion").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Buat motion di timeline").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodesWithText("Motion title").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Mode 3D").assertIsDisplayed()
    }
}

