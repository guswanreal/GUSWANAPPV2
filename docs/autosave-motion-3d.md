# Autosave, motion maker, dan 3D

Implementasi mengikuti renderer aplikasi yang sudah ada: Compose Canvas, RenderNode,
dan AGSL. Tidak ada pipeline OpenGL kedua yang menggandakan preview/ekspor.

## Cara memakai

1. Buat atau buka proyek. Perubahan disimpan otomatis; periksa status di toolbar.
   Proyek dapat dibuka kembali dari **Beranda > Proyek tersimpan**.
2. **AI / Buat Motion** membuka pembuat motion. Isi judul, subjudul, durasi,
   pilih Cinematic/Kinetic/Minimal, lalu **Buat motion di timeline**. Generator
   offline membuat layer dan keyframe yang tetap bisa diedit, di-parent, dan di-undo.
   Tombol ekspor editor menghasilkan MP4.
3. Untuk bantuan AI, isi API key Google Gemini dan instruksi di panel yang sama.
   AI menyusun rencana motion dan pilihan efek serta dapat menyarankan panel tracking.
   Hanya teks dikirim ke Google; AI tidak melihat atau menganalisis video. Rencana
   dibatasi ke properti/efek yang didukung, kemudian diterapkan melalui tombol panel.
   Key hanya hidup selama panel terbuka dan tidak dimasukkan ke file proyek/APK.
4. Pilih teks, shape, gambar, video, atau Null; buka **Transform > Mode 3D**.
   Position Z dan rotasi X/Y menambahkan perspektif. Parenting memakai matriks 4x4
   dengan offset untuk menjaga posisi saat mengganti parent.
5. Tambahkan **Optical Flares** dari browser efek. Atur posisi, intensity, size,
   streak, ghosts, dan hue; parameter bisa diberi keyframe seperti efek lainnya.
6. Pilih video dengan transform awal, buka **Track Kamera 3D**, atur FOV vertikal,
   lalu **Analisis & buat kamera 3D**. Hasilnya Camera dan Tracked Null. Aktifkan
   Mode 3D pada layer tambahan, parent ke Tracked Null, dan sesuaikan posisi lokal.

## Penyimpanan dan pemulihan

- `ProjectStore` memakai satu writer background dan antrean snapshot terbaru per
  proyek. `AtomicFile` menyelesaikan penggantian file secara atomik dan mempertahankan
  revisi valid sebelumnya sebagai cadangan. UI tidak melakukan penulisan disk.
- Layer, keyframe/easing, efek, parenting, playhead, media, hasil tracking, dan kamera
  disimpan sebagai JSON dengan schema version. Undo history hanya berada di RAM.
- Commit, undo/redo, perubahan pilihan/playhead, ON_STOP, dan keluar editor meminta
  penyimpanan. Keluar melalui tombol editor menunggu antrean penulisan selesai.
- Impor menyalin sumber dan proxy ke `filesDir/media`, bukan cache. Penghapusan
  media di galeri tidak memutus proyek baru yang sudah selesai diimpor.
- Force-close tidak menjamin instruksi terakhir yang masih menunggu penulisan.
  Pemulihan menggunakan snapshot terakhir yang selesai, atau cadangan valid bila
  file utama rusak. Menghapus data aplikasi/uninstall tetap menghapus proyek lokal.

## Batas teknis

- Mode 3D saat ini adalah bidang gambar/teks/shape, bukan teks ekstrusi. Bidang
  diurutkan dari jauh ke dekat berdasarkan pusatnya, dengan layer 2D sebagai batas
  compositing. Belum ada depth buffer untuk bidang yang saling berpotongan atau
  pemotongan poligon terhadap near plane; bidang melintasi near plane dapat hilang.
- CameraTracker3D menggunakan OpenCV: fitur sudut, optical flow LK dua arah,
  essential matrix RANSAC, triangulasi, lalu PnP RANSAC per frame. Matriks OpenCV
  dilepas per scope, frame diproses berurutan, dan analisis bisa dibatalkan.
- Ini solve monokular eksperimental dengan focal tetap dan skala relatif. Belum ada
  bundle adjustment, kalibrasi distorsi/rolling shutter, atau penambahan landmark
  ketika titik lama hilang. Latar harus relatif diam, bertekstur, dan punya paralaks.
  Rotasi saja, zoom, atau latar dominan bergerak dapat ditolak atau meleset.
- Tracking yang terputus hanya menghasilkan rentang valid, bukan mengarang pose
  untuk sisa klip. Solusi bukan pengganti penuh 3D Camera Tracker After Effects.
- API Gemini memerlukan key, akses model, dan kuota pengguna. Pembuat motion preset
  bekerja tanpa koneksi. Integrasi AI live belum diuji dengan kredensial pengguna.
- Ekspor memakai sumber media dan renderer yang sama dengan preview; ini ekspor
  visual H.264 MP4. Pipeline ini belum mencampur audio.
- Ekspor memiliki `HardwareRenderer` sendiri dan menunggu callback commit tiap
  frame sebelum melanjutkan timeline. Renderer dilepas sebelum Surface/encoder,
  termasuk ketika dibatalkan atau gagal, untuk mencegah akses BufferQueue yang
  sudah dihancurkan. Video di atas 1080p dibaca satu frame per batch.

## Pemeriksaan

`EditorAdvancedTest` mencakup round-trip proyek, penulisan terputus/cadangan,
antrean autosave dan undo, sumber impor yang dihapus dari galeri, proyeksi/parenting
3D, urutan kedalaman, validasi motion/AI, kompilasi shader, serta ekspor MP4.
`CameraTracker3DTest` memakai adegan sintetis non-planar dengan translasi kamera
dan kasus tanpa tekstur. `NullObjectUiTest` menguji Null dan pembuat motion di UI.

Jalankan `:app:connectedDebugAndroidTest` pada perangkat/emulator Android 15+;
hasil kompilasi saja tidak membuktikan shader dan encoder bekerja di GPU HP.

Hasil 10 Oktober 2026: build debug + test APK berhasil; 25/25 instrumentation tests
lulus pada emulator Android 16. Tes ekspor memverifikasi 320x180, 24 frame untuk
1 detik, serta piksel hasil render yang bervariasi. Frame MP4 juga diperiksa visual.
Log: `instrumentation-advanced.log`; gambar: `motion-export-check.png`.

API yang dipakai: [HardwareRenderer FrameRenderRequest](https://developer.android.com/reference/android/graphics/HardwareRenderer.FrameRenderRequest).
Library tes memakai Espresso 3.7.0 untuk kompatibilitas injeksi input Android 16;
lihat [catatan rilis AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test).
