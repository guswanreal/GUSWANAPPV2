package com.example.myapplication.engine

import androidx.compose.ui.graphics.Color
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Amber
import com.example.myapplication.ui.theme.Blue
import com.example.myapplication.ui.theme.Cyan
import com.example.myapplication.ui.theme.Emerald
import com.example.myapplication.ui.theme.Pink
import com.example.myapplication.ui.theme.Violet

// Built-in templates. Each one is also a worked example: its steps name the panels and
// properties used, so people can rebuild it and learn the tools along the way.

object TemplateLibrary {
    private const val D = DemoProjects.DURATION
    private var nextUid = 1000

    private fun fx(type: FxType, vararg overrides: Pair<Int, Track>) = EffectInstance.of(type, nextUid++, *overrides)
    private fun k(t: Float, v: Float, e: Easing? = null) = Keyframe(t, v, e)
    private fun anim(static: Float, vararg keys: Keyframe) = Track(static, keys.toList())

    private val introSteps = listOf(
        "Latar: layer Gradien, ditambah layer Partikel dengan efek Particular (Emitter Type 2 = kotak, Pre Run nyala).",
        "Cincin: Shape Cincin, Scale dikeyframe 0% → 150% → 115%, mode blend Add, plus efek Glow.",
        "Model 3D: Position dikeyframe di empat titik, Y Rotation 0° → 540°, efek Echo untuk jejak.",
        "Judul: Scale 220% → 100% dan Opacity 0 → 100%, efek Glow dan RGB Split.",
        "Bintang aksen: interpolasi Bounce dan wiggle() supaya terus hidup.",
    )

    val all: List<Template> = listOf(
        Template("intro", "Guswaneditor", "Motion · 3D · Video", "Intro sinematik dengan roket 3D dan judul glow", "rocket", Blue, steps = introSteps),
        Template("crystal", "Logo Reveal", "Brand kamu, berkilau", "Reveal logo dengan kristal 3D berputar", "crystal", Violet, steps = introSteps),
        Template("robot", "Tech Promo", "Diluncurkan minggu ini", "Promo produk teknologi dengan maskot robot", "robot", Cyan, steps = introSteps),
        Template("planet", "Space Opener", "Episode 01", "Opening YouTube bertema luar angkasa", "planet", Amber, steps = introSteps),
        Template("donut", "Sweet Sale", "Diskon 50% hari ini", "Iklan kuliner yang manis dan playful", "donut", Pink, steps = introSteps),
        Template("tree", "Eco Story", "Hijaukan bumi", "Cerita lingkungan dengan pohon 3D", "tree", Emerald, steps = introSteps),

        Template(
            "typo-pop", "Kinetic Pop", "Bikin video keren", "Tiga kata meletup bergantian lalu melesat keluar", "rocket", Accent,
            category = TemplateCategory.Typography,
            steps = listOf(
                "Tiap kata adalah layer Teks sendiri; Position Y menyusunnya atas-bawah.",
                "Scale dikeyframe 0% → 145% → 130% dengan interpolasi Back, jadi kata seperti meletup.",
                "Rotation -14° → 0° menambah tenaga. Tahan klip di timeline untuk mengatur jeda antarkata.",
                "Keluar: Position X ke kiri dengan Easy Ease Out, ditambah efek Motion Blur (Shutter 270°).",
                "Layer Kamera: Zoom 100% → 108% pelan supaya frame tidak diam.",
            ),
        ),
        Template(
            "typo-type", "Typewriter", "Halo, selamat datang", "Teks diketik huruf demi huruf, lengkap dengan kursor", "rocket", Blue,
            category = TemplateCategory.Typography,
            steps = listOf(
                "Efek Typewriter: nyalakan stopwatch Progress, buat key 0 lalu 1. Interpolasi Linear membuat kecepatan ketik rata.",
                "Cursor bernilai 1 menampilkan kursor yang berkedip.",
                "Subjudul memakai Text Animator: Progress 0 → 1 dengan Easy Ease; Offset Y membuat huruf naik dari bawah.",
                "Vignette di layer latar menggelapkan tepi supaya teks jadi fokus.",
            ),
        ),
        Template(
            "typo-wave", "Wave Title", "suara pantai", "Huruf muncul bergelombang dengan glow lembut", "rocket", Cyan,
            category = TemplateCategory.Typography,
            steps = listOf(
                "Text Animator: Progress 0 → 1; Offset Y 1 dan Scale From 0 membuat huruf muncul bergantian dari bawah.",
                "Wave Text menggoyang tiap huruf; atur Amplitude dan Speed sesuai selera.",
                "Deep Glow memberi cahaya berlapis yang lembut.",
                "Subjudul: animasikan Tracking 1.2 → 0.25 supaya huruf merapat.",
                "Partikel: Emitter Type 1 (garis) di bawah frame, Direction 270° (naik).",
            ),
        ),
        Template(
            "typo-glitch", "Glitch Title", "SYSTEM ERROR", "Judul dengan ledakan glitch, RGB split, dan VHS", "rocket", Pink,
            category = TemplateCategory.Typography,
            steps = listOf(
                "Glitch: nyalakan stopwatch Intensity, buat key 0 → 0.9 → 0 dalam 0,2 detik untuk satu ledakan.",
                "Ulangi ledakan di detik lain. RGB Split dan VHS memperkuat kesan rusak.",
                "Shake ikut dikeyframe pada detik yang sama supaya frame ikut terguncang.",
                "Subjudul memakai Typewriter.",
            ),
        ),
        Template(
            "typo-neon", "Neon Sign", "buka 24 jam", "Tulisan neon yang berkedip lalu menyala stabil", "rocket", Pink,
            category = TemplateCategory.Typography,
            steps = listOf(
                "Deep Glow dengan Radius besar menghasilkan cahaya neon.",
                "Strobe Light: Intensity 0.9 → 0 di awal membuat lampu berkedip lalu stabil.",
                "Subjudul: Opacity dikeyframe dengan interpolasi Hold supaya kedipnya patah-patah seperti neon rusak.",
            ),
        ),

        Template(
            "t3d-orbit", "3D Logo Orbit", "GUSWAN", "Kamera mengitari logo kristal 3D, judul berkilau", "crystal", Violet,
            category = TemplateCategory.Typography3D,
            steps = listOf(
                "Layer Kamera: Orbit Y -35° → 35° memutar sudut pandang model 3D; Zoom 115% → 100% di awal.",
                "Model: Y Rotation 0° → 360° (Linear) dan Scale dengan Back untuk muncul.",
                "Judul: Text Animator untuk reveal, lalu Shine supaya kilaunya menyapu.",
                "Partikel latar memakai Pre Run, jadi sudah penuh sejak frame pertama.",
            ),
        ),
        Template(
            "t3d-product", "Product Spin", "NEW DROP", "Robot berputar mulus dengan judul yang merapat", "robot", Amber,
            category = TemplateCategory.Typography3D,
            steps = listOf(
                "Y Rotation 0° → 720° dengan interpolasi Smooth: cepat di tengah, sangat lembut di ujung.",
                "Buka Graph Editor lalu tarik handle bezier untuk mengubah rasa putarannya.",
                "Judul: Tracking 1.2 → 0.15 (Smooth) membuat huruf merapat.",
                "Lens Flare di model memberi kesan studio. Kamera: Zoom dan Roll bergerak pelan.",
            ),
        ),
        Template(
            "t3d-depth", "Depth Parallax", "JAUH · DEKAT", "Teks dan planet di kedalaman berbeda, kamera menggeser", "planet", Emerald,
            category = TemplateCategory.Typography3D,
            steps = listOf(
                "Position Z memberi kedalaman: makin besar, makin jauh dan makin pelan bergeraknya.",
                "Layer Kamera: Point of Interest X -0.35 → 0.35 menggeser pandangan; layer jauh bergeser lebih sedikit (parallax).",
                "Orbit Y kamera ikut memutar sudut pandang model 3D.",
                "Coba ubah Position Z tiap teks lalu putar ulang.",
            ),
        ),
    )

    fun build(t: Template): List<Layer> = when (t.id) {
        "typo-pop" -> kineticPop(t)
        "typo-type" -> typewriter(t)
        "typo-wave" -> waveTitle(t)
        "typo-glitch" -> glitchTitle(t)
        "typo-neon" -> neonSign(t)
        "t3d-orbit" -> logoOrbit(t)
        "t3d-product" -> productSpin(t)
        "t3d-depth" -> depthParallax(t)
        else -> intro(t)
    }

    /** Starfield preset for Particular: slow drift, filled from frame one. */
    private fun starfield() = fx(
        FxType.Particular,
        0 to Track(18f), 1 to Track(7f), 2 to Track(0.03f), 4 to Track(90f), 6 to Track(0.25f), 7 to Track(3.2f),
        8 to Track(0.7f), 9 to Track(2.4f), 10 to Track(2f), 11 to Track(0.8f), 13 to Track(0.95f), 14 to Track(0.6f), 15 to Track(1f),
    )

    private fun intro(t: Template): List<Layer> = listOf(
        Layer(1, "Latar Gradien", LayerType.Gradient, Violet, 0f, D),
        Layer(
            2, "Partikel Bintang", LayerType.Particles, Cyan, 0f, D,
            tracks(opacity = anim(1f, k(0f, 0f), k(1.2f, 0.9f))),
            blend = BlendKind.Screen, effects = listOf(starfield()),
        ),
        Layer(
            3, "Cincin Energi", LayerType.Shape, t.accent, 0.6f, D,
            tracks(
                scale = anim(1f, k(0.6f, 0f), k(2f, 1.5f), k(3.2f, 1.15f)),
                rotation = anim(0f, k(0.6f, 0f), k(10f, 180f)),
                opacity = anim(1f, k(0.6f, 0f), k(1.4f, 1f)),
            ),
            shape = ShapeKind.Ring, effects = listOf(fx(FxType.Glow)), blend = BlendKind.Add,
        ),
        Layer(
            4, "${ModelLibrary.byId(t.modelId).name} 3D", LayerType.Model3D, Amber, 0f, D,
            tracks(
                posX = anim(0f, k(0f, -0.7f), k(3f, 0f), k(7f, 0.15f), k(10f, 0.7f)),
                posY = anim(0f, k(0f, 0.9f), k(3f, 0.05f), k(7f, 0.1f), k(10f, -1.1f)),
                scale = anim(1f, k(0f, 0.5f), k(3f, 0.85f)),
                rotation = anim(0f, k(0f, 0f), k(10f, 540f)),
            ),
            modelId = t.modelId, effects = listOf(fx(FxType.Echo)),
        ),
        Layer(
            5, "Judul Utama", LayerType.Text, Color.White, 1.5f, D,
            tracks(
                posY = Track(-0.62f),
                scale = anim(1f, k(1.5f, 2.2f), k(2.6f, 1f)),
                opacity = anim(1f, k(1.5f, 0f), k(2.2f, 1f)),
            ),
            text = t.title.uppercase(), effects = listOf(fx(FxType.Glow), fx(FxType.ChromaticAberration)),
        ),
        Layer(
            6, "Subjudul", LayerType.Text, t.accent, 2.8f, D,
            tracks(
                posY = anim(0f, k(2.8f, -0.3f), k(3.6f, -0.42f)),
                scale = Track(0.45f),
                opacity = anim(1f, k(2.8f, 0f), k(3.6f, 1f)),
            ),
            text = t.subtitle,
        ),
        Layer(
            7, "Bintang Aksen", LayerType.Shape, Emerald, 4f, 9f,
            tracks(
                posX = Track(0.72f), posY = Track(0.55f),
                scale = anim(0.35f, k(4f, 0f), k(4.6f, 0.4f), k(8.4f, 0.4f), k(9f, 0f)),
                rotation = anim(0f, k(4f, 0f), k(9f, 360f)),
            ),
            shape = ShapeKind.Star, easing = Easing.Bounce, effects = listOf(fx(FxType.Glow), fx(FxType.Wiggle)),
        ),
    )

    /** One kinetic word: pops in with Back, exits left with an accelerating ease and motion blur. */
    private fun popWord(id: Int, word: String, color: Color, y: Float, inAt: Float, outAt: Float, extra: List<EffectInstance> = emptyList()) = Layer(
        id, "Kata: $word", LayerType.Text, color, inAt, D,
        tracks(
            posY = Track(y),
            posX = anim(0f, k(outAt, 0f, Easing.EaseOut), k(outAt + 0.7f, -2.2f)),
            scale = anim(1.3f, k(inAt, 0f, Easing.Back), k(inAt + 0.45f, 1.45f, Easing.EaseInOut), k(inAt + 0.75f, 1.3f)),
            rotation = anim(0f, k(inAt, -14f, Easing.Back), k(inAt + 0.6f, 0f)),
        ),
        text = word, effects = listOf(fx(FxType.MotionBlur, 0 to Track(270f))) + extra,
    )

    private fun camera(id: Int, tracks: Map<Prop, Track>) = Layer(id, "Kamera", LayerType.Camera, Color.White, 0f, D, tracks)

    private fun kineticPop(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, Violet, 0f, D),
        popWord(3, "BIKIN", Color.White, -0.34f, 0.3f, 7.6f),
        popWord(4, "VIDEO", Color.White, 0f, 0.8f, 7.75f),
        popWord(5, "KEREN", t.accent, 0.34f, 1.3f, 7.9f, listOf(fx(FxType.Glow, 0 to Track(18f)))),
        camera(2, tracks(scale = anim(1f, k(0f, 1f, Easing.EaseInOut), k(D, 1.08f)))),
    )

    private fun typewriter(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, t.accent, 0f, D, effects = listOf(fx(FxType.Vignette, 0 to Track(0.55f)))),
        Layer(
            2, "Judul", LayerType.Text, Color.White, 0f, D,
            tracks(posY = Track(-0.08f), scale = Track(0.8f)),
            text = "Halo, selamat datang",
            effects = listOf(fx(FxType.Typewriter, 0 to anim(1f, k(0.4f, 0f, Easing.Linear), k(3.2f, 1f)))),
        ),
        Layer(
            3, "Subjudul", LayerType.Text, Color(0xFFBFD7FF), 3.3f, D,
            tracks(posY = Track(0.2f), scale = Track(0.55f)),
            text = "di Guswaneditor",
            effects = listOf(fx(FxType.TextAnimator, 0 to anim(1f, k(3.3f, 0f, Easing.EaseInOut), k(4.4f, 1f)), 2 to Track(0.6f), 4 to Track(3f))),
        ),
    )

    private fun waveTitle(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, t.accent, 0f, D),
        Layer(
            2, "Gelembung", LayerType.Particles, Color.White, 0f, D,
            tracks(posY = Track(1.1f)),
            effects = listOf(fx(FxType.Particular, 0 to Track(30f), 1 to Track(4f), 2 to Track(0.2f), 4 to Track(20f), 7 to Track(5f), 9 to Track(2.2f), 10 to Track(1f), 15 to Track(1f))),
            blend = BlendKind.Screen,
        ),
        Layer(
            3, "Judul", LayerType.Text, Color.White, 0f, D,
            tracks(scale = Track(1.8f), posY = Track(-0.05f)),
            text = "OMBAK",
            effects = listOf(
                fx(FxType.TextAnimator, 0 to anim(1f, k(0.3f, 0f, Easing.EaseInOut), k(1.8f, 1f)), 1 to Track(1f), 2 to Track(0f), 3 to Track(30f), 4 to Track(3f)),
                fx(FxType.TextWave, 0 to Track(0.18f), 1 to Track(1.2f)),
                fx(FxType.DeepGlow, 0 to Track(30f), 1 to Track(0.9f)),
            ),
        ),
        Layer(
            4, "Subjudul", LayerType.Text, Color(0xFFD9FBFF), 1.8f, D,
            tracks(posY = Track(0.36f), scale = Track(0.5f), opacity = anim(1f, k(1.8f, 0f), k(2.5f, 1f))),
            text = t.subtitle,
            effects = listOf(fx(FxType.Tracking, 0 to anim(0.25f, k(1.8f, 1.2f, Easing.EaseInOut), k(3f, 0.25f)))),
        ),
    )

    private fun glitchTitle(t: Template): List<Layer> {
        // Three bursts: 0 → peak → 0 inside a fifth of a second.
        fun bursts(peak: Float) = anim(
            0f,
            k(0.9f, 0f, Easing.Linear), k(1.0f, peak, Easing.Linear), k(1.25f, 0f, Easing.Linear),
            k(4.0f, 0f, Easing.Linear), k(4.1f, peak * 0.85f, Easing.Linear), k(4.3f, 0f, Easing.Linear),
            k(7.0f, 0f, Easing.Linear), k(7.1f, peak, Easing.Linear), k(7.4f, 0f),
        )
        return listOf(
            Layer(1, "Latar", LayerType.Gradient, Color(0xFF3A1030), 0f, D),
            Layer(
                2, "Judul", LayerType.Text, Color.White, 0f, D,
                tracks(scale = Track(1.6f), posY = Track(-0.08f)),
                text = "GLITCH",
                effects = listOf(
                    fx(FxType.Glitch, 0 to bursts(0.9f)),
                    fx(FxType.ChromaticAberration, 0 to Track(6f)),
                    fx(FxType.Shake, 0 to bursts(40f), 2 to Track(4f)),
                    fx(FxType.Vhs, 0 to Track(0.35f)),
                ),
            ),
            Layer(
                3, "Subjudul", LayerType.Text, t.accent, 1.4f, D,
                tracks(posY = Track(0.32f), scale = Track(0.5f)),
                text = t.subtitle,
                effects = listOf(fx(FxType.Typewriter, 0 to anim(1f, k(1.4f, 0f, Easing.Linear), k(2.6f, 1f)))),
            ),
        )
    }

    private fun neonSign(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, Color(0xFF1A1622), 0f, D),
        Layer(
            2, "Neon", LayerType.Text, t.accent, 0f, D,
            tracks(scale = Track(1.9f), posY = Track(-0.06f)),
            text = "NEON",
            effects = listOf(
                fx(FxType.DeepGlow, 0 to Track(50f), 1 to Track(1.4f)),
                fx(FxType.Strobe, 0 to Track(9f), 1 to anim(0f, k(0f, 0.9f, Easing.Linear), k(1.6f, 0f))),
            ),
        ),
        Layer(
            3, "Subjudul", LayerType.Text, Cyan, 2f, D,
            tracks(
                posY = Track(0.34f), scale = Track(0.55f),
                opacity = anim(1f, k(2f, 0f, Easing.Hold), k(2.1f, 1f, Easing.Hold), k(2.2f, 0.2f, Easing.Hold), k(2.35f, 1f)),
            ),
            text = t.subtitle, effects = listOf(fx(FxType.DeepGlow, 0 to Track(26f), 1 to Track(1f))),
        ),
    )

    private fun logoOrbit(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, t.accent, 0f, D),
        Layer(2, "Bintang", LayerType.Particles, Color.White, 0f, D, effects = listOf(starfield()), blend = BlendKind.Screen),
        Layer(
            3, "Logo 3D", LayerType.Model3D, t.accent, 0f, D,
            tracks(
                posY = Track(-0.1f),
                scale = anim(0.95f, k(0f, 0.4f, Easing.Back), k(1.2f, 0.95f)),
                rotation = anim(0f, k(0f, 0f, Easing.Linear), k(D, 360f)),
            ),
            modelId = t.modelId, effects = listOf(fx(FxType.Glow, 0 to Track(30f), 1 to Track(0.8f))),
        ),
        Layer(
            4, "Judul", LayerType.Text, Color.White, 0.8f, D,
            tracks(posY = Track(0.58f)),
            text = t.subtitle,
            effects = listOf(
                fx(FxType.TextAnimator, 0 to anim(1f, k(0.8f, 0f, Easing.EaseInOut), k(2.4f, 1f)), 1 to Track(0.8f), 2 to Track(0.5f)),
                fx(FxType.Shine, 0 to Track(0.4f), 3 to Track(1.6f)),
            ),
        ),
        camera(5, tracks(
            orbitY = anim(0f, k(0f, -35f, Easing.EaseInOut), k(D, 35f)),
            scale = anim(1f, k(0f, 1.15f, Easing.EaseOut), k(3f, 1f)),
        )),
    )

    private fun productSpin(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, t.accent, 0f, D),
        Layer(
            2, "Robot", LayerType.Model3D, t.accent, 0f, D,
            tracks(posY = Track(0.04f), scale = Track(0.95f), rotation = anim(0f, k(0f, 0f, Easing.Smooth), k(4f, 720f))),
            modelId = t.modelId, effects = listOf(fx(FxType.LensFlare, 0 to Track(0.9f), 1 to Track(0.35f), 2 to Track(-0.4f))),
        ),
        Layer(
            3, "Judul", LayerType.Text, Color.White, 0.5f, D,
            tracks(posY = Track(-0.62f), scale = Track(1.1f), opacity = anim(1f, k(0.5f, 0f), k(1.2f, 1f))),
            text = t.subtitle,
            effects = listOf(fx(FxType.Tracking, 0 to anim(0.15f, k(0.5f, 1.2f, Easing.Smooth), k(2.5f, 0.15f))), fx(FxType.Shine)),
        ),
        Layer(
            4, "Keterangan", LayerType.Text, t.accent, 2.4f, D,
            tracks(posY = Track(0.66f), scale = Track(0.5f)),
            text = "Robot Gus · Edisi 01",
            effects = listOf(fx(FxType.TextAnimator, 0 to anim(1f, k(2.4f, 0f, Easing.EaseInOut), k(3.4f, 1f)))),
        ),
        camera(5, tracks(
            scale = anim(1f, k(0f, 1f, Easing.Linear), k(D, 1.12f)),
            rotation = anim(0f, k(0f, -2f, Easing.EaseInOut), k(D, 2f)),
        )),
    )

    private fun depthParallax(t: Template) = listOf(
        Layer(1, "Latar", LayerType.Gradient, t.accent, 0f, D),
        Layer(
            2, "Teks jauh", LayerType.Text, Color.White, 0f, D,
            tracks(posY = Track(-0.25f), scale = Track(1.7f), opacity = Track(0.55f), posZ = Track(2.5f)),
            text = "JAUH",
        ),
        Layer(
            3, "Planet", LayerType.Model3D, t.accent, 0f, D,
            tracks(posY = Track(0.05f), scale = Track(0.8f), posZ = Track(0.8f), rotation = anim(0f, k(0f, 0f, Easing.Linear), k(D, 200f))),
            modelId = t.modelId,
        ),
        Layer(
            4, "Teks dekat", LayerType.Text, Color(0xFFE8FFF3), 0f, D,
            tracks(posY = Track(0.42f), scale = Track(1.2f), posZ = Track(-0.4f)),
            text = "DEKAT", effects = listOf(fx(FxType.DropShadow)),
        ),
        camera(5, tracks(
            posX = anim(0f, k(0f, -0.35f, Easing.EaseInOut), k(D, 0.35f)),
            orbitY = anim(0f, k(0f, -20f, Easing.EaseInOut), k(D, 20f)),
        )),
    )
}
