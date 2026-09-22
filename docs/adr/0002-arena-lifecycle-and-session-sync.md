# ADR 0002: Manajemen Sesi Arena, Concurrency Heartbeat, & Validasi Duplikasi Callsign

- **Status**: ACCEPTED
- **Tanggal**: 2026-09-22
- **Konteks**: Modul `arena-service` (Port 8082) dan engine `ArenaManager.java`.

---

## 1. Konteks & Latar Belakang Masalah
Di dalam arena multi-pemain real-time, beberapa masalah integritas sesi dapat timbul:
1. Pemain yang mencoba bergabung menggunakan callsign yang sedang aktif dimainkan oleh orang lain.
2. Pemain menekan tombol In-Game POST ("Deploy / Sync Ular") yang berpotensi merusak posisi ular atau melakukan *respawn exploit*.
3. Pemain terputus (*disconnected*) tanpa mengirim sinyal `DELETE` secara eksplisit, meninggalkan ular zombie di arena.

---

## 2. Keputusan yang Disepakati

### A. Validasi Duplikasi Callsign Real-Time (Concurrency Guard)
Di dalam `ArenaManager.joinArena`:
1. Sistem mengiterasi seluruh ular yang berstatus `isAlive == true`.
2. Jika ditemukan ular dengan `playerName` yang identik namun `playerId` berbeda:
   - Jika selisih waktu `System.currentTimeMillis() - existing.getLastHeartbeatTime() <= 5000` (masih aktif mengirim heartbeat dalam 5 detik terakhir), backend melempar `IllegalArgumentException("Nama '...' sedang digunakan oleh pemain lain di arena!")` ➔ **HTTP 400 Bad Request**.
   - Jika sesi pemain lain sudah usang (*expired* > 5 detik), instance lama dibersihkan dan pemain baru diizinkan masuk.

### B. In-Game POST (Deploy / Sync Ular Milik Sendiri)
Jika `playerId` yang dikirim sama dengan ID ular yang sudah ada di arena (`existing.getPlayerId().equalsIgnoreCase(playerId)`):
- Ular lama dihapus dari daftar `snakes`.
- Ular baru diinstansiasi dengan **mempertahankan koordinat segmen `segments`, arah `direction`, dan perolehan skor `score`** yang sedang berjalan.
- Hal ini mencegah perpindahan lokasi acak saat pemain menekan tombol sinkronisasi in-game.

### C. Operasi REST In-Game
- **POST** (`/api/v1/arena/snakes`): Bergabung ke arena atau sinkronisasi state ular aktif.
- **GET** (`/api/v1/arena/snakes/{id}`): Mengambil data telemetri real-time ular (panjang segmen, koordinat kepala, skor).
- **PUT** (`/api/v1/arena/snakes/{id}/loadout`): Mengganti loadout taktis (`snakeType`) dan warna/skin (`color`) secara langsung di arena tanpa mengubah nama pemain.
- **PATCH** (`/api/v1/arena/snakes/{id}/eat`): Mengkonsumsi apel valid (`EatAppleInput(appleId)`), menambah skor dan panjang tubuh ular.
- **PATCH** (`/api/v1/arena/snakes/{id}/skill?active=true`): Mengaktifkan kemampuan khusus kelas taktis ular.
- **DELETE** (`/api/v1/arena/snakes/{id}`): Mengeluarkan ular dari arena (HTTP 204) dan memicu sinkronisasi skor akhir ke `player-service` melalui `PlayerServiceClient`.

---

## 3. Konsekuensi & Dampak
- Sesi permainan tetap konsisten dan aman dari sabotase duplikasi nama.
- Tidak ada ular hantu/zombie yang tertinggal permanen di grid pertempuran.
