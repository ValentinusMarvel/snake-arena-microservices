# Snake Arena: Clash of Serpents (Microservices REST API)

Proyek Tugas Mata Kuliah **Arsitektur Berbasis Layanan (ABL)**.

- **Nama:** Valentinus Marvel
- **NIM:** 245150201111027

## Arsitektur
1. **player-service (Port 8081)**: Manajemen profil pemain, kelas ular (Viper, Python, Cobra), skor persisten, dan API Versioning (v1 vs v2 Breaking Changes).
2. **rena-service (Port 8082)**: Gameplay arena session, makanan/apel, tabrakan, leaderboard live, dan komunikasi inter-service.
3. **rontend**: Web game HTML5 Canvas responsif (PC/Mobile) dengan Live REST API Inspector.
