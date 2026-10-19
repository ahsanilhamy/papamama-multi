# PapaMama Multi-Operator

**Papa Mama tenang, anak selalu terhubung.**

PapaMama Multi-Operator adalah aplikasi Android untuk memantau **sisa kuota, paket telepon, pulsa, masa aktif, dan masa tenggang** banyak nomor keluarga dalam satu layar, termasuk nomor di **smartwatch anak (imoo dll.)**, lintas operator:

> Telkomsel · simPATI · Halo · by.U · XL · Indosat · Tri · Smartfren

Final Project *Belajar Lagi*, dikembangkan dari ide PapaMama (Polaris IdeaSpark #20) yang awalnya hanya untuk Telkomsel.

---

## Masalah yang diselesaikan

- Keluarga punya 5+ nomor. Dua di antaranya ada di jam anak yang **tidak bisa menampilkan notifikasi paket**.
- Untuk mengecek sisa paket dan masa aktif, orang tua harus membuka aplikasi operator satu per satu.
- Kalau masa aktif lewat tanpa disadari, nomor bisa hangus. Akibatnya harus ganti nomor, dan **live tracking anak terputus**.

## Fitur

| Fitur | Keterangan |
|---|---|
| **Satu layar semua nomor** | Kartu per anggota keluarga dengan status *Aman / Segera / Kritis / Tenggang / Cek ulang*. Nomor bisa ditambah tanpa batas (tombol **+**). |
| **Label Jam anak** | Semua peringatan nomor di jam anak dikirim ke HP orang tua. |
| **Alert** | Kuota sisa **20% dan 5%**, masa aktif **H-7, H-3, H-1**, masuk **masa tenggang**, pulsa di bawah batas, dan pengingat bila data sudah lama tidak diperbarui. |
| **Penjaga Otomatis** | Saat kritis, peringatan diulang setiap hari sampai beres, dengan tombol *Perpanjang* 1 ketukan langsung dari notifikasi. |
| **Beli paket tanpa buka banyak app** | Satu tombol untuk menu paket operator (USSD langsung dari SIM yang tepat), *Kirim pulsa* dari nomor lain sesama operator, atau e-wallet/m-banking yang terpasang (DANA, GoPay, OVO, ShopeePay, BRImo, dll.). Nomor tujuan otomatis disalin. |
| **Catat paket** | Setelah kembali dari aplikasi pembayaran, PapaMama menawarkan "Catat paket baru" (2 ketukan) agar kuota dan masa aktif langsung akurat. |
| **Riwayat & belanja bulanan** | Ringkasan pembelian per nomor. |

## Seberapa "live"? Jujur soal batasannya

Operator Indonesia **tidak menyediakan API publik** untuk aplikasi pihak ketiga. Karena itu PapaMama memakai beberapa jalur, dari yang paling otomatis:

| Posisi nomor | Cara update | Seberapa otomatis |
|---|---|---|
| **SIM ada di HP ini** (1–2 SIM) | USSD otomatis di latar belakang setiap 3/6/12/24 jam, plus membaca SMS info kuota dari operator | ✅ Otomatis penuh |
| **HP keluarga lain** (mis. HP Mama) | Pasang PapaMama di HP tersebut, nyalakan **Mode Pelapor**. Status dikirim lewat ±1 SMS/hari ke HP orang tua dan dibaca otomatis. | ✅ Otomatis (biaya SMS normal) |
| **Jam anak** (SIM di jam) | (1) **Catat paket** saat membeli, sehingga masa aktif tepat dan kuota diperkirakan dari pemakaian rata-rata (dipelajari otomatis, awal ±30 MB/hari). (2) **Tempel teks / Bagikan → PapaMama** dari SMS atau aplikasi operator, dan angkanya dibaca otomatis. | ⚠️ Semi-otomatis: masa aktif akurat, kuota berupa perkiraan berlabel "±" |

Fitur *Watch Offline* di deck PapaMama asli membutuhkan data jaringan operator atau API brand jam, sehingga **tidak bisa** dibuat oleh aplikasi pihak ketiga. Sebagai gantinya, ada pengingat "data perlu diperbarui".

## Privasi

- Semua data **hanya tersimpan di HP** (tidak ada server dan tidak ada login operator).
- Tidak ada data lokasi atau isi pesan anak yang diambil.
- SMS yang dibaca hanya SMS dari pengirim operator (nama atau kode pendek) dan laporan berformat `#PM#` yang **berasal dari nomor itu sendiri**.

## Izin yang dipakai (semua opsional)

| Izin | Untuk |
|---|---|
| Notifikasi | Peringatan |
| Telepon (`CALL_PHONE`) | Cek USSD otomatis dan membuka menu paket |
| Status telepon | Mengenali SIM 1/SIM 2 |
| Terima SMS | Membaca SMS info kuota dan laporan keluarga |
| Kirim SMS | Hanya untuk Mode Pelapor |

## Kode USSD bawaan

Kode diambil dari sumber publik. Operator bisa mengubahnya sewaktu-waktu, jadi **semua kode bisa diedit di Pengaturan → Kode USSD operator**.

| Operator | Cek | Menu beli | Transfer pulsa |
|---|---|---|---|
| Telkomsel / simPATI | `*888#` | `*363#` | `*858#` |
| Halo | `*888#` | `*363#` | – |
| by.U | `*888#` | (via app by.U) | – |
| XL | `*123#`, `*123*7*1#` | `*123#` | `*123*168#` |
| Indosat | `*123#` | `*123#` | `*123*151#` |
| Tri | `*111*1#`, `*111*4*2*1*1#` | `*123#` | `*323#` |
| Smartfren | `*999#`, `*995#` | `*123#` | `*879#` |

## Cara pasang

1. Unduh `PapaMama.apk` dari halaman **Releases** (atau tab **Actions → Build APK → Artifacts**).
2. Buka file di HP Android (minimal Android 8). Izinkan "Instal dari sumber tidak dikenal" bila diminta.
3. Buka PapaMama, lalu **tambah nomor**. Untuk melihat contoh, buka **Pengaturan → Isi data contoh**.

> Karena aplikasi meminta izin SMS dan Telepon dan dipasang di luar Play Store, Google Play Protect bisa menampilkan peringatan. Pilih "Tetap instal".

## Build sendiri

```bash
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Atau buka folder ini di Android Studio (Ladybug atau lebih baru). Setiap `git push` otomatis membangun APK, menjalankan unit test, dan mengambil screenshot dari emulator lewat GitHub Actions.

## Struktur kode

```
app/src/main/java/id/papamama/app/
├── model/Models.kt              # Operator (8), nomor, pengaturan
├── core/OperatorTextParser.kt   # Membaca teks USSD/SMS operator → kuota, pulsa, tanggal
├── core/Logic.kt                # Status (Aman/Segera/...), aturan alert, estimasi kuota
├── core/Telephony.kt            # USSD otomatis, SIM, Mode Pelapor, buka app lain
├── core/Background.kt           # Notifikasi, penerima SMS, pekerjaan berkala (WorkManager)
├── data/Store.kt                # Penyimpanan lokal (JSON)
└── ui/                          # Jetpack Compose: Beranda, Detail, Tambah/Ubah, lembar Beli/Update, Pengaturan
```

Teknologi: Kotlin, Jetpack Compose (Material 3), WorkManager. Tanpa server.

---

*Angka pada data contoh bersifat fiktif.*
