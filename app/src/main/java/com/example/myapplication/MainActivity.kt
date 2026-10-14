package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.myapplication.engine.FootageImporter
import com.example.myapplication.engine.ImportedModels
import com.example.myapplication.engine.shaderCompileErrors
import com.example.myapplication.engine.ProjectSettings
import com.example.myapplication.engine.Template
import com.example.myapplication.engine.ProjectSnapshot
import com.example.myapplication.engine.ProjectStore
import com.example.myapplication.ui.screens.NewProjectSheet
import com.example.myapplication.ui.editor.VideoEditorScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.ModelsScreen
import com.example.myapplication.ui.screens.Studio3DScreen
import com.example.myapplication.ui.screens.TemplatesScreen
import com.example.myapplication.ui.theme.Ink850
import com.example.myapplication.ui.theme.Ink900
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GuswanApp()
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Rounded.Home),
    TEMPLATES("Template", Icons.Rounded.Widgets),
    MODELS("Model 3D", Icons.Rounded.ViewInAr),
}

/** Full-screen workspaces opened on top of the tabbed shell. */
sealed interface Workspace {
    data class Editor(val settings: ProjectSettings, val template: Template?, val restored: ProjectSnapshot? = null) : Workspace
    data class Studio(val modelId: String?) : Workspace
}

@Composable
fun GuswanApp() {
    var tab by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var workspace by remember { mutableStateOf<Workspace?>(null) }
    // Non-null while the "new project" sheet is open; it carries the template to start from, if any.
    var newProject by remember { mutableStateOf<NewProjectRequest?>(null) }
    val context = LocalContext.current
    val store = remember(context) { ProjectStore.get(context) }
    var savedProjects by remember { mutableStateOf<List<ProjectSnapshot>>(emptyList()) }
    LaunchedEffect(workspace) {
        if (workspace == null) {
            store.flush()
            savedProjects = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.list() }
        }
    }
    LaunchedEffect(Unit) {
        ImportedModels.load(context.applicationContext)
        // Debug builds verify every AGSL effect shader compiles on this GPU.
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val errors = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { shaderCompileErrors() }
            if (errors.isEmpty()) android.util.Log.i("GuswanFx", "All effect shaders compiled")
            errors.forEach { android.util.Log.e("GuswanFx", it) }
        }
    }

    AnimatedContent(
        workspace,
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
        label = "workspace",
        modifier = Modifier.fillMaxSize().background(Ink950),
    ) { ws ->
        when (ws) {
            is Workspace.Editor -> Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                VideoEditorScreen(ws.settings, ws.template, onBack = { workspace = null }, restored = ws.restored)
            }
            is Workspace.Studio -> Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Studio3DScreen(ws.modelId, onBack = { workspace = null })
            }
            null -> Shell(tab, onTab = { tab = it }, open = { workspace = it }, create = { newProject = NewProjectRequest(it) }, savedProjects = savedProjects)
        }
    }

    newProject?.let { request ->
        NewProjectSheet(
            template = request.template,
            onDismiss = { newProject = null },
            onCreate = { settings ->
                newProject = null
                workspace = Workspace.Editor(settings, request.template)
            },
        )
    }
}

@Composable
private fun Shell(tab: AppDestinations, onTab: (AppDestinations) -> Unit, open: (Workspace) -> Unit, create: (Template?) -> Unit, savedProjects: List<ProjectSnapshot>) {
    val itemColors = NavigationSuiteItemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Accent, selectedTextColor = TextHigh, indicatorColor = Accent.copy(alpha = 0.16f),
            unselectedIconColor = TextLow, unselectedTextColor = TextLow,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Accent, selectedTextColor = TextHigh, indicatorColor = Accent.copy(alpha = 0.16f),
            unselectedIconColor = TextLow, unselectedTextColor = TextLow,
        ),
        navigationDrawerItemColors = NavigationSuiteDefaults.itemColors().navigationDrawerItemColors,
    )
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = null) },
                    label = { Text(it.label) },
                    selected = it == tab,
                    onClick = { onTab(it) },
                    colors = itemColors,
                )
            }
        },
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = Ink850,
            navigationRailContainerColor = Ink850,
        ),
        containerColor = Ink900,
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val pad = PaddingValues(top = 8.dp, bottom = 24.dp)
            AnimatedContent(tab, label = "tab", transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }) { t ->
                when (t) {
                    AppDestinations.HOME -> HomeScreen(
                        savedProjects = savedProjects,
                        onResume = { open(Workspace.Editor(it.settings, null, it)) },
                        contentPadding = pad,
                        onOpenTemplate = { create(it) },
                        onNewVideo = { create(null) },
                        onNewScene = { open(Workspace.Studio(null)) },
                        onOpenModel = { open(Workspace.Studio(it)) },
                        onSeeTemplates = { onTab(AppDestinations.TEMPLATES) },
                        onSeeModels = { onTab(AppDestinations.MODELS) },
                    )
                    AppDestinations.TEMPLATES -> TemplatesScreen(pad) { create(it) }
                    AppDestinations.MODELS -> ModelsScreen(pad, onOpenModel = { open(Workspace.Studio(it)) }, onNewScene = { open(Workspace.Studio(null)) })
                }
            }
        }
    }
}

/** A pending "new project" request; [template] is null for an empty composition. */
private class NewProjectRequest(val template: Template?)
