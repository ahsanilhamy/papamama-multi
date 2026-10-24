# Tasks

Maksimal 8 tugas terkecil yang bisa dites, urut dari yang paling penting. Rinciannya ada di [plan.md](plan.md).

- [ ] **1. Jawab 6 keputusan terbuka di plan.md** (prabayar/pascabayar, lokasi SIM, OS HP anak, arti "go live", batas "manual", kanal peringatan).
  *Tes:* tidak ada lagi butir "belum dijawab" di plan.md, dan tiap jawaban tertulis.

- [ ] **2. Kumpulkan minimal 5 contoh teks SMS/USSD Telkomsel dari 2 nomor** (kuota normal, hampir habis, masa aktif dekat, masa tenggang) sebagai fixtures. Nomor dan data pribadi disamarkan.
  *Tes:* folder fixtures berisi ≥5 sampel dan tidak ada nomor telepon asli.

- [ ] **3. Buat parser teks → `sisa_kuota` dan `masa_aktif`.**
  *Tes:* unit test lulus untuk semua fixtures, dan teks yang tidak dikenali menghasilkan hasil "tidak terbaca", bukan angka tebakan.

- [ ] **4. Buat logika status Aman / Segera / Kritis / Tenggang** dari tanggal dan sisa kuota (ambang awal: H-7, H-3, H-1; kuota 20% dan 5%).
  *Tes:* unit test dengan tanggal fiktif menghasilkan status yang benar di tiap batas ambang.

- [ ] **5. Ambil data otomatis untuk 1 nomor** lewat USSD dan SMS terjadwal di latar belakang.
  *Tes:* di HP sungguhan, tanpa menyentuh app, data nomor itu berubah dalam 24 jam.

- [ ] **6. Tampilkan 2 nomor dalam satu layar** (satu kartu per nomor, berisi status, sisa kuota, masa aktif).
  *Tes:* dengan data dari fixtures, dua kartu tampil dengan status yang sesuai tugas 4.

- [ ] **7. Kirim peringatan ke HP orang tua** saat H-3 dan kuota menipis, diulang harian sampai ditangani.
  *Tes:* simulasi tanggal H-3 memunculkan notifikasi di HP orang tua, dan hari berikutnya muncul lagi.

- [ ] **8. Uji tertutup 7 hari berturut-turut tanpa input manual**, lalu putuskan rilis.
  *Tes:* selama 7 hari 0 masa aktif terlewat tanpa peringatan, dan 0 kali orang tua perlu memasukkan data manual.
