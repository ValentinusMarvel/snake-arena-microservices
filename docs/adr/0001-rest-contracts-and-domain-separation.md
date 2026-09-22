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
   - Endpoint **v2** (`/api/v2/players`) mengadopsi struktur nested `classProfile` (`passiveEffect`, `activeAbility`) dan secara otomatis menghitung `playerTier` (BRONZE, SILVER, GOLD, MASTER).
   - Skema v2 menolak (*breaking change*) payload flat v1 dengan HTTP 400 Bad Request untuk menjamin integritas DTO v2.

2. **Operasi RESTful**:
   - **POST**: Mendaftarkan profil pemain baru. Menolak nama duplikat jika sudah terdaftar di database.
   - **GET**: Mengambil data profil pemain (tunggal atau daftar).
   - **PUT**: *Full replace* representasi pemain (`username` dan kelas taktis). Nama pemain dapat diubah pada fase lobi.
   - **PATCH**: Partial update data statistik (`/stats`) yang menerima `{ score, matchCompleted }`. Menghitung ulang tier pada v2.
   - **DELETE**: Menghapus data akun pemain dari database (HTTP 204 No Content).

### B. Auto-Recovery Saat Registrasi Lobi
Jika nama yang dimasukkan pengguna sudah ada di basis data H2 `player-service`:
- Request `POST /api/v1/players` mengembalikan HTTP 400 Bad Request (*"Username sudah digunakan"*).
- Frontend secara otomatis melakukan *fallback* dengan memanggil `GET /api/v1/players`, mengambil `playerId` yang bersesuaian, dan menyinkronkan profil tanpa memunculkan pesan error yang mengganggu alur pemain.

---

## 3. Konsekuensi & Trade-Off
- **Positif**: Pemisahan jelas antara akun permanen dan sesi game volatile. Frontend dapat bertransisi antara v1 dan v2 secara mulus.
- **Batasan**: DTO v2 tidak boleh dilonggarkan menjadi flat agar kontrak arsitektur versioning tetap teruji dan valid.
