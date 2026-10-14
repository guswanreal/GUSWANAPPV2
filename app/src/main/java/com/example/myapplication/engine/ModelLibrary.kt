package com.example.myapplication.engine

import androidx.compose.ui.graphics.Color

enum class ModelCategory(val label: String) { Primitive("Primitif"), Object("Objek"), Character("Karakter"), Nature("Alam"), Imported("Impor") }

/** A ready-made model shipped with Guswaneditor. */
data class LibraryModel(
    val id: String,
    val name: String,
    val category: ModelCategory,
    val color: Color,
    val parts: List<Part>,
    /** How far the model's base sits below its origin, so it can rest on the grid. */
    val restHeight: Float = 1f,
) {
    val triangleCount: Int get() = parts.sumOf { it.mesh.triangleCount }
}

private fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)
private fun s(u: Float) = Vec3(u, u, u)

object ModelLibrary {
    private val brown = Color(0xFF8B5A2B)
    private val leaf = Color(0xFF22C55E)
    private val leafDark = Color(0xFF15803D)
    private val white = Color(0xFFF1F5F9)
    private val glass = Color(0xFF7DD3FC)
    private val dark = Color(0xFF1E293B)
    private val red = Color(0xFFEF4444)
    private val gold = Color(0xFFFBBF24)

    val primitives = listOf(
        LibraryModel("cube", "Kubus", ModelCategory.Primitive, Color(0xFF3B82F6), listOf(Part(Primitives.cube))),
        LibraryModel("sphere", "Bola", ModelCategory.Primitive, Color(0xFFEC4899), listOf(Part(Primitives.sphere))),
        LibraryModel("cylinder", "Silinder", ModelCategory.Primitive, Color(0xFF8B5CF6), listOf(Part(Primitives.cylinder))),
        LibraryModel("cone", "Kerucut", ModelCategory.Primitive, Color(0xFFF59E0B), listOf(Part(Primitives.cone))),
        LibraryModel("torus", "Torus", ModelCategory.Primitive, Color(0xFF22D3EE), listOf(Part(Primitives.torus)), restHeight = 0.38f),
        LibraryModel("pyramid", "Piramida", ModelCategory.Primitive, Color(0xFFEAB308), listOf(Part(Primitives.pyramid))),
        LibraryModel("gem", "Ikosahedron", ModelCategory.Primitive, Color(0xFF10B981), listOf(Part(Primitives.gem))),
        LibraryModel("plane", "Bidang", ModelCategory.Primitive, Color(0xFF64748B), listOf(Part(Primitives.plane, scale = s(1.6f))), restHeight = 0f),
    )

    val showcase = listOf(
        LibraryModel(
            "rocket", "Roket", ModelCategory.Object, white,
            listOf(
                Part(Primitives.cylinder, v(0f, 0f, 0f), v(0.5f, 1.1f, 0.5f)),
                Part(Primitives.cone, v(0f, 1.65f, 0f), v(0.5f, 0.55f, 0.5f), color = red),
                Part(Primitives.sphere, v(0f, 0.45f, 0.45f), v(0.2f, 0.2f, 0.1f), color = glass),
                Part(Primitives.cube, v(0.55f, -0.85f, 0f), v(0.3f, 0.3f, 0.04f), color = red),
                Part(Primitives.cube, v(-0.55f, -0.85f, 0f), v(0.3f, 0.3f, 0.04f), color = red),
                Part(Primitives.cube, v(0f, -0.85f, -0.55f), v(0.04f, 0.3f, 0.3f), color = red),
                Part(Primitives.cone, v(0f, -1.3f, 0f), v(0.32f, 0.22f, 0.32f), rotation = v(180f, 0f, 0f), color = Color(0xFFF97316)),
            ),
            restHeight = 1.52f,
        ),
        LibraryModel(
            "robot", "Robot Gus", ModelCategory.Character, Color(0xFF94A3B8),
            listOf(
                Part(Primitives.cube, v(0f, 0f, 0f), v(0.7f, 0.75f, 0.45f)),
                Part(Primitives.cube, v(0f, 1.2f, 0f), v(0.5f, 0.42f, 0.42f), color = Color(0xFFCBD5E1)),
                Part(Primitives.cube, v(0f, 1.2f, 0.4f), v(0.38f, 0.18f, 0.04f), color = dark),
                Part(Primitives.sphere, v(-0.17f, 1.22f, 0.45f), s(0.08f), color = Color(0xFF22D3EE)),
                Part(Primitives.sphere, v(0.17f, 1.22f, 0.45f), s(0.08f), color = Color(0xFF22D3EE)),
                Part(Primitives.cylinder, v(0f, 1.8f, 0f), v(0.03f, 0.2f, 0.03f), color = dark),
                Part(Primitives.sphere, v(0f, 2.02f, 0f), s(0.09f), color = Color(0xFFEC4899)),
                Part(Primitives.cube, v(0f, 0.1f, 0.46f), v(0.3f, 0.25f, 0.02f), color = Color(0xFF3B82F6)),
                Part(Primitives.cylinder, v(-0.95f, 0.05f, 0f), v(0.16f, 0.6f, 0.16f), rotation = v(0f, 0f, -12f)),
                Part(Primitives.cylinder, v(0.95f, 0.05f, 0f), v(0.16f, 0.6f, 0.16f), rotation = v(0f, 0f, 12f)),
                Part(Primitives.cube, v(-0.32f, -1.15f, 0f), v(0.2f, 0.4f, 0.22f), color = dark),
                Part(Primitives.cube, v(0.32f, -1.15f, 0f), v(0.2f, 0.4f, 0.22f), color = dark),
            ),
            restHeight = 1.55f,
        ),
        LibraryModel(
            "tree", "Pohon Pinus", ModelCategory.Nature, leaf,
            listOf(
                Part(Primitives.cylinder, v(0f, -0.7f, 0f), v(0.18f, 0.5f, 0.18f), color = brown),
                Part(Primitives.cone, v(0f, 0.1f, 0f), v(0.95f, 0.65f, 0.95f), color = leafDark),
                Part(Primitives.cone, v(0f, 0.75f, 0f), v(0.75f, 0.55f, 0.75f)),
                Part(Primitives.cone, v(0f, 1.3f, 0f), v(0.5f, 0.45f, 0.5f), color = Color(0xFF4ADE80)),
            ),
            restHeight = 1.2f,
        ),
        LibraryModel(
            "house", "Rumah", ModelCategory.Object, Color(0xFFFDE68A),
            listOf(
                Part(Primitives.cube, v(0f, 0f, 0f), v(0.9f, 0.7f, 0.8f)),
                Part(Primitives.pyramid, v(0f, 1.2f, 0f), v(1.25f, 0.5f, 1.15f), rotation = v(0f, 45f, 0f), color = Color(0xFFDC2626)),
                Part(Primitives.cube, v(0f, -0.3f, 0.81f), v(0.2f, 0.4f, 0.02f), color = brown),
                Part(Primitives.cube, v(-0.5f, 0.2f, 0.81f), v(0.17f, 0.17f, 0.02f), color = glass),
                Part(Primitives.cube, v(0.5f, 0.2f, 0.81f), v(0.17f, 0.17f, 0.02f), color = glass),
                Part(Primitives.cube, v(0.5f, 1.3f, -0.2f), v(0.12f, 0.35f, 0.12f), color = Color(0xFF78716C)),
            ),
            restHeight = 0.7f,
        ),
        LibraryModel(
            "donut", "Donat", ModelCategory.Object, Color(0xFFD9A066),
            listOf(
                Part(Primitives.torus),
                Part(Primitives.torus(1f, 0.4f, 26, 12), v(0f, 0.12f, 0f), v(1f, 0.55f, 1f), color = Color(0xFFF472B6)),
                Part(Primitives.cube, v(0.6f, 0.38f, 0.5f), v(0.1f, 0.025f, 0.025f), rotation = v(0f, 30f, 0f), color = gold),
                Part(Primitives.cube, v(-0.7f, 0.38f, 0.2f), v(0.1f, 0.025f, 0.025f), rotation = v(0f, -50f, 0f), color = Color(0xFF60A5FA)),
                Part(Primitives.cube, v(0.1f, 0.38f, -0.9f), v(0.1f, 0.025f, 0.025f), rotation = v(0f, 80f, 0f), color = white),
                Part(Primitives.cube, v(-0.3f, 0.38f, 0.85f), v(0.1f, 0.025f, 0.025f), rotation = v(0f, 10f, 0f), color = Color(0xFF4ADE80)),
            ),
            restHeight = 0.38f,
        ),
        LibraryModel(
            "planet", "Planet Cincin", ModelCategory.Nature, Color(0xFFF59E0B),
            listOf(
                Part(Primitives.sphere, scale = s(0.9f)),
                Part(Primitives.torus(1.55f, 0.12f, 36, 6), scale = v(1f, 0.15f, 1f), rotation = v(18f, 0f, 10f), color = Color(0xFFFDE68A)),
                Part(Primitives.lowSphere, v(1.9f, 0.9f, -0.6f), s(0.18f), color = Color(0xFF94A3B8)),
            ),
            restHeight = 0.9f,
        ),
        LibraryModel(
            "mushroom", "Jamur", ModelCategory.Nature, red,
            listOf(
                Part(Primitives.cylinder, v(0f, -0.35f, 0f), v(0.28f, 0.55f, 0.28f), color = Color(0xFFFEF3C7)),
                Part(Primitives.sphere, v(0f, 0.25f, 0f), v(0.95f, 0.5f, 0.95f)),
                Part(Primitives.lowSphere, v(0.4f, 0.6f, 0.35f), s(0.12f), color = white),
                Part(Primitives.lowSphere, v(-0.45f, 0.55f, 0.1f), s(0.14f), color = white),
                Part(Primitives.lowSphere, v(0.05f, 0.72f, -0.4f), s(0.1f), color = white),
            ),
            restHeight = 0.9f,
        ),
        LibraryModel(
            "crystal", "Kristal", ModelCategory.Nature, Color(0xFFA78BFA),
            listOf(
                Part(Primitives.gem, v(0f, 0.3f, 0f), v(0.4f, 1.1f, 0.4f)),
                Part(Primitives.gem, v(0.55f, -0.2f, 0.1f), v(0.28f, 0.7f, 0.28f), rotation = v(0f, 0f, -25f), color = Color(0xFFC4B5FD)),
                Part(Primitives.gem, v(-0.5f, -0.25f, 0.2f), v(0.25f, 0.6f, 0.25f), rotation = v(15f, 0f, 28f), color = Color(0xFF8B5CF6)),
                Part(Primitives.gem, v(0.05f, -0.45f, 0.5f), v(0.2f, 0.4f, 0.2f), rotation = v(30f, 0f, 0f), color = Color(0xFFDDD6FE)),
                Part(Primitives.cylinder, v(0f, -0.82f, 0f), v(0.9f, 0.08f, 0.9f), color = Color(0xFF475569)),
            ),
            restHeight = 0.9f,
        ),
    )

    private val builtIn = primitives + showcase

    /** Built-in models followed by the user's imports; reading it inside Compose tracks imports as state. */
    val all: List<LibraryModel> get() = builtIn + ImportedModels.models

    /** Falls back to the cube when an imported model was deleted or hasn't loaded yet. */
    fun byId(id: String): LibraryModel =
        builtIn.firstOrNull { it.id == id } ?: ImportedModels.models.firstOrNull { it.id == id } ?: primitives.first()
}
