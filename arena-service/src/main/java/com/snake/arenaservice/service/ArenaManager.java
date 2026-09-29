package com.snake.arenaservice.service;

import com.snake.arenaservice.client.PlayerServiceClient;
import com.snake.arenaservice.domain.*;
import com.snake.arenaservice.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArenaManager {

    private static final Logger log = LoggerFactory.getLogger(ArenaManager.class);

    private final int gridWidth = 30;
    private final int gridHeight = 30;
    private final int maxPlayers = 16;
    private final int maxApples = 6; // Batas maksimal apel di arena dalam satu waktu
    private int tickCounter = 0;

    private final Map<String, Snake> snakes = new ConcurrentHashMap<>();
    private final Map<String, Apple> apples = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final PlayerServiceClient playerServiceClient;

    public ArenaManager(PlayerServiceClient playerServiceClient) {
        this.playerServiceClient = playerServiceClient;

        // Inisialisasi apel awal dengan jumlah seimbang (4 Normal, 1 Golden)
        spawnApple("APL-1", "NORMAL", 10);
        spawnApple("APL-2", "NORMAL", 10);
        spawnApple("APL-3", "NORMAL", 10);
        spawnApple("APL-4", "NORMAL", 10);
        spawnApple("APL-5", "GOLDEN", 30);

        // Inisialisasi bot simulasi
        joinArena("BOT-01", "Bot-Viper", "VIPER", "#00ff88", 6, 6);
        joinArena("BOT-02", "Bot-Python", "PYTHON", "#00d4ff", 22, 22);
    }

    public synchronized Apple spawnApple(String id, String type, int points) {
        if (apples.size() >= maxApples) {
            return null;
        }

        int x, y;
        int attempts = 0;
        do {
            x = random.nextInt(gridWidth - 4) + 2;
            y = random.nextInt(gridHeight - 4) + 2;
            attempts++;
        } while (isLocationOccupiedByApple(x, y) && attempts < 20);

        Apple apple = new Apple(id, x, y, type, points);
        apples.put(id, apple);
        return apple;
    }

    private boolean isLocationOccupiedByApple(int x, int y) {
        for (Apple a : apples.values()) {
            if (a.x() == x && a.y() == y) return true;
        }
        return false;
    }

        private boolean isBot(Snake snake) {
        if (snake == null) return false;
        return (snake.getPlayerId() != null && snake.getPlayerId().startsWith("BOT")) ||
               (snake.getPlayerName() != null && snake.getPlayerName().toLowerCase().startsWith("bot"));
    }

public synchronized Snake joinArena(String playerId, String playerName, String snakeType, String color, int startX, int startY) {
        long now = System.currentTimeMillis();

        // 1. Bersihkan ular lama milik player ID yang sama jika ada, simpan referensi jika masih hidup
        Snake existingSnake = null;
        for (Snake existing : new ArrayList<>(snakes.values())) {
            if (existing.getPlayerId() != null && existing.getPlayerId().equalsIgnoreCase(playerId)) {
                existingSnake = existing;
                snakes.remove(existing.getId());
            }
        }

        // 2. Proteksi Duplikasi Nama: Jangan pernah membunuh ular pemain lain!
        // Jika nama bertabrakan dengan pemain lain yang masih aktif, otomatis berikan tag unik baru.
        String resolvedPlayerName = playerName;
        boolean conflict;
        do {
            conflict = false;
            for (Snake existing : snakes.values()) {
                if (existing.isAlive() && !existing.getPlayerId().equalsIgnoreCase(playerId)
                        && existing.getPlayerName().equalsIgnoreCase(resolvedPlayerName)) {
                    conflict = true;
                    String base = resolvedPlayerName.replaceAll("#\\d+$", "");
                    int newTag = 1000 + random.nextInt(9000);
                    resolvedPlayerName = base + "#" + newTag;
                    break;
                }
            }
        } while (conflict);

        if (snakes.size() >= maxPlayers) {
            throw new IllegalStateException("Arena penuh! Maksimum " + maxPlayers + " pemain telah tercapai.");
        }

        String snakeId = "SNK-" + (System.currentTimeMillis() % 100000) + "-" + random.nextInt(100);
        List<Point> segments;
        String dir;
        int score = 0;
        boolean skillActive = false;
        long skillActivatedAt = 0;
        long skillCooldownUntil = 0;

        if (existingSnake != null && existingSnake.isAlive() && existingSnake.getSegments() != null && !existingSnake.getSegments().isEmpty()) {
            // Pertahankan koordinat segmen, arah pergerakan, skor, serta status cooldown skill agar tidak terjadi cooldown-reset exploit saat in-game POST
            segments = new ArrayList<>(existingSnake.getSegments());
            dir = existingSnake.getDirection();
            score = existingSnake.getScore();
            skillActive = existingSnake.isSkillActive();
            skillActivatedAt = existingSnake.getSkillActivatedAt();
            skillCooldownUntil = existingSnake.getSkillCooldownUntil();
        } else {
            segments = new ArrayList<>();
            segments.add(new Point(startX, startY));
            segments.add(new Point(startX, (startY + 1) % gridHeight));
            segments.add(new Point(startX, (startY + 2) % gridHeight));
            dir = "UP";
        }

        String actualColor = color != null && !color.isBlank() ? color : getDefaultColor(snakeType);
        Snake snake = new Snake(snakeId, playerId, resolvedPlayerName, snakeType.toUpperCase(), actualColor, segments, dir, score, true, skillActive);
        snake.setSkillActivatedAt(skillActivatedAt);
        snake.setSkillCooldownUntil(skillCooldownUntil);
        snakes.put(snakeId, snake);
        log.info("Snake spawned: {} ({}) for player {} at ({},{}) [retainedPosition={}]", resolvedPlayerName, snakeId, playerId, segments.get(0).x(), segments.get(0).y(), existingSnake != null);
        return snake;
    }

    public Snake joinArena(String playerId, String playerName, String snakeType, String color) {
        int startX = random.nextInt(gridWidth - 10) + 5;
        int startY = random.nextInt(gridHeight - 10) + 5;
        return joinArena(playerId, playerName, snakeType, color, startX, startY);
    }

    private String getDefaultColor(String type) {
        if ("VIPER".equalsIgnoreCase(type)) return "#00ff88";
        if ("PYTHON".equalsIgnoreCase(type)) return "#00d4ff";
        if ("COBRA".equalsIgnoreCase(type)) return "#ffd700";
        return "#ffffff";
    }

    public ArenaState getArenaState() {
        List<LeaderboardEntry> leaderboard = snakes.values().stream()
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(s -> new LeaderboardEntry(s.getId(), s.getPlayerName(), s.getSnakeType(), s.getScore()))
                .toList();

        return new ArenaState(gridWidth, gridHeight, maxPlayers, snakes.size(),
                new ArrayList<>(snakes.values()),
                new ArrayList<>(apples.values()),
                leaderboard);
    }

    public Snake getSnake(String snakeId) {
        Snake snake = snakes.get(snakeId);
        if (snake == null) {
            throw new ResourceNotFoundException("Ular dengan ID '" + snakeId + "' tidak ada di arena");
        }
        return snake;
    }

    public synchronized Snake changeDirection(String snakeId, String newDir) {
        Snake snake = getSnake(snakeId);
        snake.updateHeartbeat();
        String current = snake.getDirection();
        if (newDir != null) {
            newDir = newDir.toUpperCase();
            boolean opposite = ("UP".equals(current) && "DOWN".equals(newDir)) ||
                               ("DOWN".equals(current) && "UP".equals(newDir)) ||
                               ("LEFT".equals(current) && "RIGHT".equals(newDir)) ||
                               ("RIGHT".equals(current) && "LEFT".equals(newDir));
            if (!opposite) {
                snake.setDirection(newDir);
            }
        }
        return snake;
    }

    public synchronized Snake updatePosition(String snakeId, List<Point> segments, String direction) {
        Snake snake = getSnake(snakeId);
        if (segments != null && !segments.isEmpty()) {
            snake.setSegments(new ArrayList<>(segments));
        }
        if (direction != null && !direction.isBlank()) {
            snake.setDirection(direction.toUpperCase());
        }
        return snake;
    }

    public synchronized Snake updateLoadout(String snakeId, String snakeType, String color) {
        Snake snake = getSnake(snakeId);
        snake.setSnakeType(snakeType.toUpperCase());
        if (color != null && !color.isBlank()) {
            snake.setColor(color);
        } else {
            snake.setColor(getDefaultColor(snakeType));
        }
        return snake;
    }

    public synchronized Snake eatApple(String snakeId, String appleId) {
        Snake snake = getSnake(snakeId);
        Apple apple = apples.remove(appleId);
        int pointsGained = apple != null ? apple.points() : 10;

        if ("COBRA".equalsIgnoreCase(snake.getSnakeType()) && apple != null && "GOLDEN".equals(apple.type())) {
            pointsGained += 20;
        }

        snake.setScore(snake.getScore() + pointsGained);

        if (!snake.getSegments().isEmpty()) {
            Point tail = snake.getSegments().get(snake.getSegments().size() - 1);
            snake.getSegments().add(new Point(tail.x(), tail.y()));
        }

        // Spawn terkontrol: hanya jika total apel di arena < 4 agar map tetap seimbang dan bersih
        if (apples.size() < 4) {
            boolean isGolden = random.nextInt(10) > 7;
            spawnApple("APL-" + (System.currentTimeMillis() % 10000) + "-" + random.nextInt(100),
                    isGolden ? "GOLDEN" : "NORMAL",
                    isGolden ? 30 : 10);
        }
        return snake;
    }

    public synchronized Snake toggleSkill(String snakeId, boolean active) {
        Snake snake = getSnake(snakeId);
        snake.updateHeartbeat();
        long now = System.currentTimeMillis();

        if (active) {
            if (snake.isSkillActive()) {
                return snake;
            }
            if (now < snake.getSkillCooldownUntil()) {
                long remainingSec = (snake.getSkillCooldownUntil() - now + 999) / 1000;
                throw new IllegalStateException("Skill masih dalam masa cooldown! Silakan tunggu " + remainingSec + " detik lagi.");
            }
            snake.setSkillActive(true);
            snake.setSkillActivatedAt(now);
            // Durasi aktif 3 detik + Cooldown 5 detik
            snake.setSkillCooldownUntil(now + 3000 + 5000);
            log.info("Skill aktif untuk ular {} (durasi: 3s, cooldown: 5s)", snake.getPlayerName());
        } else {
            snake.setSkillActive(false);
        }
        return snake;
    }

    public synchronized Snake heartbeat(String snakeId) {
        Snake snake = getSnake(snakeId);
        snake.updateHeartbeat();
        return snake;
    }

    public synchronized Snake eliminateSnake(String snakeId) {
        Snake snake = snakes.remove(snakeId);
        if (snake == null) {
            throw new ResourceNotFoundException("Ular dengan ID '" + snakeId + "' tidak ditemukan di arena");
        }
        snake.setAlive(false);
        return snake;
    }

    /**
     * Engine loop server-side: Dijalankan setiap 120ms.
     * Mengkalkulasi pergerakan dan tabrakan PvP.
     * Ular mati TIDAK lagi menjatuhkan apel agar peta tidak banjir apel.
     */
    @Scheduled(fixedRate = 120)
    public synchronized void serverTick() {
        if (snakes.isEmpty()) return;

        tickCounter++;
        // Spawn apel berkala yang seimbang: setiap ~5 detik (40 ticks) jika apel kurang dari maxApples
        if (tickCounter % 40 == 0 && apples.size() < maxApples) {
            boolean isGolden = random.nextInt(10) > 7;
            spawnApple("APL-" + (System.currentTimeMillis() % 10000) + "-" + random.nextInt(100),
                    isGolden ? "GOLDEN" : "NORMAL",
                    isGolden ? 30 : 10);
        }

        Map<String, Point> nextHeads = new HashMap<>();
        Set<String> eliminatedIds = new HashSet<>();
        long now = System.currentTimeMillis();

        // 0. Deteksi ular yatim (ghost snake) yang terputus / ditinggal refresh (> 7 detik tanpa heartbeat)
        for (Snake snake : snakes.values()) {
            if (isBot(snake)) {
                snake.updateHeartbeat();
            } else if (now - snake.getLastHeartbeatTime() > 7000) {
                log.info("Client timeout: Ular {} ({}) terputus/refresh window, otomatis dibersihkan",
                        snake.getPlayerName(), snake.getId());
                eliminatedIds.add(snake.getId());
            }

            // Auto-expire skill setelah durasi 3 detik selesai
            if (snake.isSkillActive() && (now - snake.getSkillActivatedAt() > 3000)) {
                snake.setSkillActive(false);
                log.info("Durasi skill ular {} berakhir (3 detik), masuk cooldown 5 detik", snake.getPlayerName());
            }
        }

        // 1. Hitung koordinat kepala berikutnya untuk semua ular yang hidup
        for (Snake snake : snakes.values()) {
            if (!snake.isAlive() || snake.getSegments().isEmpty()) continue;

            if (isBot(snake) && random.nextInt(10) > 7) {
                String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT"};
                changeDirection(snake.getId(), dirs[random.nextInt(dirs.length)]);
            }

            Point head = snake.getSegments().get(0);
            int dx = 0;
            int dy = 0;
            switch (snake.getDirection()) {
                case "UP" -> dy = -1;
                case "DOWN" -> dy = 1;
                case "LEFT" -> dx = -1;
                case "RIGHT" -> dx = 1;
            }

            int nextX = (head.x() + dx + gridWidth) % gridWidth;
            int nextY = (head.y() + dy + gridHeight) % gridHeight;
            nextHeads.put(snake.getId(), new Point(nextX, nextY));
        }

        // 2. Deteksi tabrakan (PvP Collision Detection)

        for (Snake snake : snakes.values()) {
            if (!snake.isAlive()) continue;

            Point nextHead = nextHeads.get(snake.getId());
            if (nextHead == null) continue;

            // Jika kelas Python mengaktifkan perisai (Armor Shield), kebal dari tabrakan
            boolean isShieldActive = "PYTHON".equalsIgnoreCase(snake.getSnakeType()) && snake.isSkillActive();
            if (isShieldActive) {
                continue;
            }

            // Periksa tabrakan dengan ular lain atau diri sendiri
            for (Snake other : snakes.values()) {
                if (!other.isAlive()) continue;

                if (other.getId().equals(snake.getId())) {
                    // Tabrakan dengan badan sendiri (mulai segmen ke-3)
                    for (int i = 3; i < snake.getSegments().size(); i++) {
                        Point seg = snake.getSegments().get(i);
                        if (seg.x() == nextHead.x() && seg.y() == nextHead.y()) {
                            eliminatedIds.add(snake.getId());
                            log.info("Self-collision: Ular {} menabrak badannya sendiri", snake.getPlayerName());
                            break;
                        }
                    }
                } else {
                    // Tabrakan dengan segmen ular lain (PvP Collision)
                    for (Point otherSeg : other.getSegments()) {
                        if (otherSeg.x() == nextHead.x() && otherSeg.y() == nextHead.y()) {
                            eliminatedIds.add(snake.getId());
                            other.setScore(other.getScore() + 50); // Kill reward
                            log.info("PvP Collision: Kepala {} menabrak badan {}, {} tereliminasi!",
                                    snake.getPlayerName(), other.getPlayerName(), snake.getPlayerName());
                            break;
                        }
                    }
                }

                if (eliminatedIds.contains(snake.getId())) {
                    break;
                }
            }
        }

        // 3. Proses ular-ular yang tereliminasi (TANPA DROP APEL)
        for (String id : eliminatedIds) {
            Snake deadSnake = snakes.remove(id);
            if (deadSnake != null) {
                deadSnake.setAlive(false);

                // Sinkronisasi skor akhir ke Player Service
                try {
                    playerServiceClient.syncPlayerScore(deadSnake.getPlayerId(), deadSnake.getScore(), true);
                } catch (Exception e) {
                    log.warn("Sinkronisasi skor gagal untuk {}", deadSnake.getPlayerName());
                }

                // Bot yang gugur TIDAK respawn lagi untuk balancing gameplay
                if (isBot(deadSnake)) {
                    log.info("Bot {} tereliminasi secara permanen (TIDAK respawn lagi untuk menjaga balance arena).",
                            deadSnake.getPlayerName());
                }
            }
        }

        // 4. Majukan posisi ular yang selamat & proses konsumsi apel
        for (Snake snake : snakes.values()) {
            if (!snake.isAlive()) continue;

            Point nextHead = nextHeads.get(snake.getId());
            if (nextHead == null) continue;

            String eatenAppleId = null;
            for (Apple apple : apples.values()) {
                if (apple.x() == nextHead.x() && apple.y() == nextHead.y()) {
                    eatenAppleId = apple.id();
                    break;
                }
            }

            snake.getSegments().add(0, nextHead);

            if (eatenAppleId != null) {
                eatApple(snake.getId(), eatenAppleId);
            } else {
                if (snake.getSegments().size() > 3) {
                    snake.getSegments().remove(snake.getSegments().size() - 1);
                }
            }
        }
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}
