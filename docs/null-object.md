# Null Object dan parenting 2D

## Menggunakan fitur

1. Buka editor, tekan **Tambah layer**, lalu pilih **Null Object**.
2. Pilih layer konten melalui timeline, buka **Parent**, dan pilih Null tersebut.
3. Pilih Null pada timeline, lalu ubah atau animasikan Position, Scale, Rotation, atau Anchor di **Transform**.
4. Pilih **Tidak ada** di panel Parent untuk melepas hubungan. Menghapus parent juga melepas child dengan mempertahankan tampilan pada playhead aktif.

Null hanya menampilkan kontrol saat dipilih di editor. Null tidak menghasilkan piksel pada preview tanpa seleksi maupun ekspor. Opacity, visibility, solo, dan rentang waktu Null tidak menyembunyikan child. Pilih Null dari timeline; Null tidak mengambil hit-test konten pada preview.

## Integrasi

Pipeline aplikasi saat ini memakai Compose Canvas / RenderNode. `LayerHierarchy` menggunakan `android.graphics.Matrix`, lalu mengonversi hasil ke matriks Compose. Evaluator dibagi oleh semua layer untuk setiap timestamp dalam frame, termasuk sampel motion blur. Preview dan ekspor menggunakan `drawComposition` yang sama.

Konvensi proyek yang sudah ada dipertahankan: PosX/PosY bernilai 0 di tengah komposisi; anchor dalam satuan setengah lebar/tinggi komposisi; Scale seragam dengan 1 = 100%; Rotation dalam derajat. Parenting ini adalah transformasi planar 2D. Kamera dan rotasi model 3D tetap memiliki perilaku khusus renderer yang ada.

Untuk vektor kolom:

```
local = T(position) * R(rotation) * S(scale) * T(-anchor)
parentSpace = parentWorld * T(-compositionCenter)
basis = parentSpace * parentOffset
world = basis * local
```

Pada root, parentSpace adalah identitas. Parent-offset menyimpan enam koefisien affine immutable dalam koordinat komposisi ternormalisasi. Ini menjaga kompensasi konsisten antara preview dan ekspor dengan rasio aspek yang sama serta aman untuk snapshot undo/redo.

Saat mengganti parent:

```
newOffset = inverse(newParentSpace) * oldBasis
```

Tidak perlu menginvers matriks child atau memecah matriks menjadi sudut dan skala. Child berskala nol tetap bisa dipindah; parent baru dengan matriks singular ditolak dengan pesan di panel. Siklus dan parent yang tidak ada ditolak. Hierarki valid tidak dibatasi delapan tingkat.

Kompensasi mempertahankan tampilan pada timestamp pengeditan. Melepas parent yang bergerak tidak membekukan seluruh lintasan animasinya ke keyframe child; baking animasi belum ditambahkan. Perubahan rasio aspek setelah parenting juga memerlukan evaluasi ulang kompensasi.

## Verifikasi

```
gradlew.bat :app:assembleDebug :app:testDebugUnitTest
gradlew.bat :app:connectedDebugAndroidTest
```

`LayerHierarchyTest` memeriksa urutan transformasi dan anchor, hierarki 40 tingkat, penolakan siklus, reparent/detach, kesesuaian preview-ekspor, skala nol, gestur, hapus/undo/redo, serta render Null tanpa piksel. `NullObjectUiTest` memeriksa menu tambah Null dan panel Parent.

Salinan file sebelum perubahan ada di `backups/null-object-before/` karena workspace tidak memiliki repository Git.
