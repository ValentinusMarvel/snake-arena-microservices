# ADR 0001: Pemisahan Domain Microservices & Spesifikasi Kontrak REST (v1 & v2)

- **Status**: ACCEPTED
- **Tanggal**: 2026-09-22
- **Konteks**: Arsitektur backend `player-service` (Port 8081) dan `arena-service` (Port 8082).

---

## 1. Konteks & Latar Belakang Masalah
Sistem game Snake Arena dibagi menjadi dua layanan microservices independen:
1. **`player-service` (Port 8081)**: Bertanggung jawab atas persistensi data pemain, profil, skor kumulatif, dan sistem leveling/tier.
2. **`arena-service` (Port 8082)**: Bertanggung jawab atas kalkulasi fisika game real-time, tabrakan, pergerakan ular, kemunculan apel, dan status arena in-game.

Diperlukan pemisahan tanggung jawab yang ketat (*separation of concerns*) serta pemeliharaan kompatibilitas antarmuka (*API versioning*) antara versi legacy (v1) dan versi modern bertingkat RPG (v2).

---

## 2. Keputusan yang Disepakati

### A. Kontrak REST `player-service` (Port 8081)
1. **v1 vs v2 Versioning Strategy**:
   - Endpoint **v1** (`/api/v1/players`) menggunakan struktur DTO flat sederhana: `username` dan `snakeType` (`VIPER`, `PYTHON`, `COBRA`).
   - Endpoint **v2** (`/api/v2/players`) mengadopsi struktur nested `classProfile` (`passiveEffect`, `activeAbility`) dan secara otomatis menghitung `playerTier` (`Bronze`, `Silver`, `Gold`, `Master`).
   - Skema v2 menolak (*breaking change*) payload flat v1 dengan HTTP 400 Bad Request untuk menjamin integritas DTO v2.

2. **Operasi RESTful**:
   - **POST**: Mendaftarkan profil pemain baru. Menolak nama duplikat jika sudah terdaftar di database.
   - **GET**: Mengambil data profil pemain (tunggal atau daftar).
   - **PUT**: *Full replace* representasi pemain (`username` dan kelas taktis). Nama pemain dapat diubah pada fase lobi.
   - **PATCH**: Partial update data statistik (`/stats`) yang menerima `{ score, matchCompleted }`. Menghitung ulang tier pada v2.
   - **DELETE**: Menghapus data akun pemain dari database (HTTP 204 No Content).

### B. Sistem Tag Unik / Discriminator (Opsi C: Ala Discord & Battle.net)
Untuk mencegah pembajakan identitas (*identity hijacking*) dan percampuran skor tertinggi (*shared highscore*) antar-perangkat saat pemain menggunakan nama panggilan yang sama tanpa sistem otentikasi password:
1. **Format Identitas**: `BaseCallsign#XXXX` (contoh: `Viper#1024`, `Pilot#5821`).
2. **Isolasi Profil Antar-Perangkat**:
   - Setiap perangkat browser menyimpan profil lokalnya (`localStorage`: `{ baseName, tag, playerId }`).
   - Device A dengan nama `Viper` mendapatkan tag unik (misal `Viper#1024`), sementara Device B yang juga memasukkan nama `Viper` mendapatkan tag terpisah (misal `Viper#5821`).
   - Keduanya terdaftar sebagai entitas `PlayerEntity` terpisah di `player-service`, sehingga memiliki `playerId`, statistik pertandingan, dan *high score* masing-masing yang 100% independen.
3. **Multiplayer Tanpa Konflik Callsign**:
   - Di arena pertempuran (`arena-service`), kedua ular diidentifikasi dengan nama lengkap bertag (`Viper#1024` dan `Viper#5821`).
   - Keduanya dapat bertarung bersamaan di satu arena tanpa terbentur validasi duplikasi nama.
4. **Auto-Tag Resolution pada Backend**:
   - Jika endpoint `POST` menerima nama tanpa tag yang sudah terdaftar di database, backend otomatis menghasilkan tag 4-digit unik untuk menghindari kegagalan pendaftaran.

---

## 3. Konsekuensi & Trade-Off
- **Positif**:
  - Skor tertinggi dan akun antar-perangkat tidak lagi bercampur tanpa izin.
  - Pemain bebas memilih nama panggilan apa saja tanpa khawatir ditolak karena sudah dipakai orang lain.
  - Kompatibilitas mundur (*backward compatibility*) tetap terjaga dengan seluruh unit test.
- **Batasan**: Batas maksimal karakter username pada DTO diperluas hingga 30 karakter untuk menampung tag `#XXXX`.
