# ADR 0003: Kontrol Masukan Klien, Sinkronisasi State Ular, & Live Leaderboard

- **Status**: ACCEPTED
- **Tanggal**: 2026-09-22
- **Konteks**: Modul `frontend` (`app.js`, `api.js`, `index.html`) dan static assets `arena-service`.

---

## 1. Konteks & Latar Belakang Masalah
Pada antarmuka klien game:
1. Pemain membutuhkan responsivitas kontrol instan untuk kemampuan taktis (skill) menggunakan tombol keyboard `Spasi`, `E`, dan `F`.
2. Pilihan kelas ular pada dua panel antarmuka (panel kiri chassis dan overlay tengah lobi) harus selalu sinkron secara dua arah.
3. State `snakeId` harus sinkron antara closure internal `app.js` dan namespace global `window.snakeId` agar request `PATCH /skill` tidak mengirimkan ID usang (stale) yang berujung pada error HTTP 404.
4. Papan peringkat (*Leaderboard*) harus menampilkan ranking pemain aktif secara real-time dengan penanda khusus bagi pemain lokal.

---

## 2. Keputusan yang Disepakati

### A. Keybinds Skill Masukan Keyboard
Di dalam event listener `keydown` [app.js](../../frontend/js/app.js):
- Tombol `" "`, `"e"`, `"E"`, `"f"`, dan `"F"` diarahkan secara langsung memanggil `triggerSkill()`.
- Pemanggilan memicu animasi visual glow/partikel pada canvas dan mengirim request `PATCH /api/v1/arena/snakes/{id}/skill?active=true` ke backend.

### B. Sinkronisasi Dua Arah Kartu Kelas
- Seluruh elemen kartu kelas overlay tengah memiliki atribut eksplisit `data-class="VIPER"`, `data-class="PYTHON"`, dan `data-class="COBRA"`.
- Fungsi `selectSidebarClass` memperbarui kelas CSS `.active` di sidebar kiri dan `.selected` di overlay tengah.
- Fungsi `selectOverlayClass` melakukan hal simetris sebaliknya.

### C. Sinkronisasi Identitas `snakeId` & Self-Healing
- Dibuat fungsi `setSnakeId(id)` di `app.js` dan diekspos ke `window.setSnakeId`.
- Setiap kali modul `api.js` mengeksekusi in-game POST atau respawn, `window.setSnakeId(newSnakeId)` dipanggil untuk menjaga konsistensi state.
- Fungsi `triggerSkill()` menerapkan proteksi *self-healing*: jika `snakeId` lokal tidak ditemukan di `window.arenaSnakes`, fungsi otomatis mengambil ID ular aktif milik user dari daftar ular arena atau `window.snakeId`.

### D. Papan Peringkat (Live Leaderboard)
- Selector container diselaraskan pada ID `leaderboardContainer`.
- Data diurutkan secara menurun (*descending*) berdasarkan skor.
- Menampilkan lencana peringkat podium (`#1`, `#2`, `#3`) dan penanda khusus `(ANDA)` pada ular lokal.

---

## 3. Sinkronisasi Aset Frontend
Seluruh perubahan pada folder `frontend/` wajib disinkronkan secara identik ke:
1. `arena-service/src/main/resources/static/`
2. `arena-service/target/classes/static/`
sehingga server game menyajikan versi klien yang mutakhir tanpa inkonsistensi cache.
