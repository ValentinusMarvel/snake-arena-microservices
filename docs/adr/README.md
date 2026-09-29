# Architecture Decision Records (ADR) Index

Dokumen ini mencatat seluruh keputusan arsitektural, kontrak API, pemisahan domain, dan aturan bisnis yang disepakati untuk ekosistem **Snake Arena Microservices**. Seluruh pengembang dan kontributor wajib mematuhi panduan dan batasan dalam ADR ini.

## Daftar Keputusan Arsitektur

| No. | Judul ADR | Status | Tanggal | Ruang Lingkup |
|:---:|---|:---:|:---:|---|
| **[0001](0001-rest-contracts-and-domain-separation.md)** | Pemisahan Domain Microservices & Spesifikasi Kontrak REST (v1 & v2) | **ACCEPTED** | 2026-09-22 | `player-service`, `arena-service`, REST API |
| **[0002](0002-arena-lifecycle-and-session-sync.md)** | Manajemen Sesi Arena, Concurrency Heartbeat, & Validasi Duplikasi Callsign | **ACCEPTED** | 2026-09-22 | `arena-service`, In-Game Engine, Concurrency |
| **[0003](0003-client-controls-and-state-management.md)** | Kontrol Masukan Klien, Sinkronisasi State Ular, & Live Leaderboard | **ACCEPTED** | 2026-09-22 | `frontend`, Canvas HUD, Event Keybinds |
| **[0004](0004-docker-actuator-and-resilience.md)** | Kontainerisasi Docker Compose, Observabilitas Spring Boot Actuator, & Pola Ketahanan Resilience4j | **ACCEPTED** | 2026-09-29 | `devops`, `docker`, `actuator`, `resilience4j` |

