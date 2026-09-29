# ADR 0004: Kontainerisasi Docker Compose, Observabilitas Spring Boot Actuator, dan Pola Ketahanan Resilience4j

- **Status**: ACCEPTED
- **Tanggal**: 2026-09-29
- **Konteks**: Penguatan standar enterprise microservices pada `player-service` dan `arena-service` mengacu pada tolak ukur buku *Microservices with Spring Boot and Spring Cloud (4th Edition)* (Magnus Larsson) dan *Mastering RESTful Web Services with Java*.

---

## 1. Konteks & Latar Belakang Masalah
Sebelumnya, sistem `snake-arena-microservices` dijalankan sebagai aplikasi Java lokal mandiri tanpa kontainerisasi dan tanpa mekanisme pemantauan kesehatan terstandarisasi. Komunikasi antar-layanan (*inter-service communication*) dari `arena-service` ke `player-service` saat pertandingan berakhir juga rentan mengalami *cascading failure* jika `player-service` mengalami *down* atau lonjakan latensi.

Untuk memenuhi standar arsitektur microservices enterprise:
1. Layanan harus dapat diorkestrasi dan dideploy secara terisolasi dan mudah melalui kontainer (Docker & Docker Compose).
2. Orkestrator kontainer memerlukan *liveness & readiness probe* untuk mendeteksi kesiapan aplikasi.
3. Respons kesalahan (*error response*) harus memiliki struktur terstandarisasi dan dapat dilacak asal layanannya.
4. Komunikasi sinkron antar-layanan harus dilindungi oleh pola *Circuit Breaker* untuk mencegah kehabisan sumber daya komputasi (*thread starvation*).

---

## 2. Keputusan yang Disepakati

### A. Kontainerisasi Multi-Stage Build & Docker Compose (Larsson Ch. 4)
1. **Multi-Stage Dockerfile**:
   - **Stage 1 (Builder)**: Menggunakan image `eclipse-temurin:21-jdk-jammy` untuk mengunduh dependensi dan mengompilasi kode sumber (`./mvnw clean package -DskipTests`).
   - **Stage 2 (Runtime)**: Menggunakan image minimal `eclipse-temurin:21-jre-jammy`, mengisolasi proses di bawah pengguna non-root (`appuser:1001`), dan menyematkan instruksi `HEALTHCHECK` berkala melalui `/actuator/health`.
2. **Orkestrasi Docker Compose (`docker-compose.yml`)**:
   - Membentuk bridge network terisolasi `snake-network`.
   - Menghubungkan dependensi layanan: `arena-service` hanya mulai menerima beban kerja setelah `player-service` berstatus `service_healthy`.
   - Menggunakan resolusi DNS internal Docker: `PLAYER_SERVICE_URL=http://player-service:8081`.
   - Menyediakan skrip otomasi eksekusi `run_docker.bat` / `run_docker.sh` dan penghentian `stop_docker.bat` / `stop_docker.sh`.

### B. Observabilitas & Health Probes (Spring Boot Actuator - Larsson Ch. 20)
1. Menambahkan pustaka `spring-boot-starter-actuator` pada kedua layanan.
2. Mengekspos endpoint pemantauan `/actuator/health`, `/actuator/info`, dan `/actuator/metrics`.
3. Mengaktifkan *readiness* dan *liveness* state (`management.endpoint.health.probes.enabled=true`).

### C. Standarisasi Error Model `HttpErrorInfo` (Larsson Ch. 3)
1. Mengganti variasi response error lokal dengan model seragam `HttpErrorInfo` yang memuat:
   - `timestamp`: Waktu presisi terjadinya insiden (ISO-8601).
   - `path`: URI endpoint yang memicu kegagalan.
   - `status` & `error`: Kode numerik HTTP dan deskripsi standarnya.
   - `message` / `detail`: Penjelasan kegagalan yang manusiawi.
   - `serviceAddress`: Identitas hostname/IP dan port mikroservis yang merespon (`ServiceUtil`).
2. Menyatukan penanganan exception melalui `@RestControllerAdvice` (`GlobalExceptionHandler`).

### D. Pola Ketahanan Resilience4j Circuit Breaker (Larsson Ch. 13 & Mehta Ch. 6)
1. Menambahkan pustaka `resilience4j-spring-boot3` dan `spring-boot-starter-aop` pada `arena-service`.
2. Menerapkan anotasi `@CircuitBreaker(name = "playerService", fallbackMethod = "syncScoreFallback")` pada method `PlayerServiceClient.syncPlayerScore(...)`.
3. Jika tingkat kegagalan pemanggilan `player-service` melampaui ambang batas (*failure rate threshold* 50% dalam window 5 panggilan), Circuit Breaker akan membuka (**OPEN**):
   - Panggilan sinkronisasi langsung dialihkan ke `syncScoreFallback(...)` tanpa menunggu timeout jaringan.
   - Skor pertandingan diamankan dalam memori lokal, dan status kesehatan Circuit Breaker tercatat di Actuator.
   - Tidak terjadi crash atau *blocking* pada siklus hidup mesin game arena.

---

## 3. Konsekuensi Positif
- **Portabilitas & Konsistensi**: Aplikasi dapat dijalankan dengan sekali klik/perintah di seluruh sistem operasi yang mendukung Docker.
- **Kesiapan Produksi (Production-Ready)**: Healthcheck otomatis mendeteksi kontainer yang tidak sehat.
- **Isolasi Kegagalan (Fault Isolation)**: Kerusakan pada `player-service` tidak melumpuhkan permainan aktif di `arena-service`.
- **Keterlacakan Diagnostik**: Setiap pesan kesalahan secara transparan menyertakan `serviceAddress` yang bertanggung jawab.
