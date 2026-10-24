# Plan: Pantau Kuota Anak

Rencana singkat mewujudkan [BRIEF.md](BRIEF.md). Status: **draf**, masih ada keputusan terbuka (lihat bawah).

## Tujuan

Orang tua tahu **masa aktif dan sisa paket data 2 nomor anak** tanpa harus mengecek atau mengisi data manual, dan **diperingatkan sebelum masa aktif lewat**.

## Yang sudah diketahui

| Hal | Isi | Sumber |
|---|---|---|
| Jumlah nomor | 2 | Brief |
| Operator | Telkomsel untuk kedua nomor | Jawaban pemilik |
| Masalah utama | Masa aktif sering terlewat | Brief |
| Acuan | Aplikasi PapaMama di repo ini (Android, Kotlin, Compose, WorkManager) | Brief + README repo ini |

Catatan dari README repo ini: operator Indonesia tidak menyediakan API publik, jadi data diambil lewat USSD otomatis dan pembacaan SMS info kuota. Build sideload yang meminta izin SMS diblokir Google Play Protect. Dua hal ini menentukan pilihan di bawah.

## Keputusan terbuka (belum dijawab, jangan ditebak)

1. **Prabayar atau pascabayar** untuk tiap nomor? Mengubah sumber data dan arti "masa aktif".
2. **SIM anak ada di mana**: HP anak, HP orang tua, atau jam tangan anak? Menentukan apakah perlu peran "Pelapor" di HP anak.
3. **HP anak Android atau iOS?** iOS tidak mengizinkan baca SMS, sehingga jalur otomatis tertutup.
4. **"Go live" berarti apa**: terbit di Play Store, atau cukup terpasang dan jalan terus di HP sendiri? Menentukan perlunya backend dan kebijakan izin SMS Play Store.
5. **"Gak manual update"**: boleh ada setup satu kali (izin, tambah nomor), atau benar-benar nol input?
6. **Kanal peringatan**: notifikasi di app, WhatsApp, atau SMS ke orang tua?

## Pendekatan (usulan, bergantung jawaban di atas)

1. **Sumber data**: kirim USSD cek paket Telkomsel secara berkala di latar belakang dan baca SMS balasannya. Tidak memakai API tidak resmi atau login MyTelkomsel, karena rapuh dan berisiko melanggar ketentuan.
2. **Parser teks**: ubah teks SMS/USSD menjadi `sisa_kuota` dan `masa_aktif`. Dilindungi unit test dari contoh teks nyata, karena format bisa berubah.
3. **Logika status**: Aman / Segera / Kritis / Tenggang dari sisa hari dan sisa kuota. Ambang awal mengikuti PapaMama (masa aktif H-7, H-3, H-1; kuota 20% dan 5%), bisa diubah.
4. **Dua nomor, satu layar**: satu kartu per nomor.
5. **Jika SIM ada di HP anak**: peran "Pelapor" mengirim ringkasan ke HP orang tua. Jalur transport (SMS atau server kecil) diputuskan setelah keputusan #2 dan #4.
6. **Peringatan otomatis**: notifikasi saat H-7, H-3, H-1 dan kuota menipis, diulang harian sampai ditangani.

## Urutan kerja

1. Jawab keputusan terbuka.
2. Kumpulkan contoh teks Telkomsel dan buat parser + logika status (inti, bisa dites tanpa HP).
3. Pengambilan data otomatis untuk satu nomor, lalu dua nomor.
4. Peringatan ke HP orang tua.
5. Uji tertutup 7 hari tanpa input manual, lalu rilis.

## Risiko

- Format SMS/USSD Telkomsel berubah, dan parser berhenti membaca.
- Izin SMS dibatasi di Play Store atau diblokir Play Protect saat sideload.
- Android mematikan tugas latar belakang demi baterai, dan cek berkala terlewat.
- Privasi: data hanya untuk keluarga sendiri. Hindari menyimpan isi pesan selain laporan kuota.
