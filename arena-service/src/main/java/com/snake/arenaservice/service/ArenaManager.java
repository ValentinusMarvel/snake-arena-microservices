package com.snake.arenaservice.service;

import com.snake.arenaservice.domain.*;
import com.snake.arenaservice.exception.ResourceNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArenaManager {

    private final int gridWidth = 30;
    private final int gridHeight = 30;
    private final int maxPlayers = 16;
    private final Map<String, Snake> snakes = new ConcurrentHashMap<>();
    private final Map<String, Apple> apples = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ArenaManager() {
        // Inisialisasi apel awal
        spawnApple("APL-1", "NORMAL", 10);
        spawnApple("APL-2", "NORMAL", 10);
        spawnApple("APL-3", "GOLDEN", 30);
        spawnApple("APL-4", "NORMAL", 10);
        spawnApple("APL-5", "GOLDEN", 30);

        // Inisialisasi bot simulasi
        joinArena("BOT-01", "Bot-Viper", "VIPER", "#00ff88", 6, 6);
        joinArena("BOT-02", "Bot-Python", "PYTHON", "#00d4ff", 22, 22);
    }

    public synchronized Apple spawnApple(String id, String type, int points) {
        int x = random.nextInt(gridWidth - 4) + 2;
        int y = random.nextInt(gridHeight - 4) + 2;
        Apple apple = new Apple(id, x, y, type, points);
        apples.put(id, apple);
        return apple;
    }

    public synchronized Snake joinArena(String playerId, String playerName, String snakeType, String color, int startX, int startY) {
        if (snakes.size() >= maxPlayers) {
            throw new IllegalStateException("Arena penuh! Maksimum " + maxPlayers + " pemain telah tercapai.");
        }

        String snakeId = "SNK-" + (System.currentTimeMillis() % 100000) + "-" + random.nextInt(100);
        List<Point> segments = new ArrayList<>();
        segments.add(new Point(startX, startY));
        segments.add(new Point(startX, (startY + 1) % gridHeight));
        segments.add(new Point(startX, (startY + 2) % gridHeight));

        String actualColor = color != null && !color.isBlank() ? color : getDefaultColor(snakeType);
        Snake snake = new Snake(snakeId, playerId, playerName, snakeType.toUpperCase(), actualColor, segments, "UP", 0, true, false);
        snakes.put(snakeId, snake);
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
        String current = snake.getDirection();
        if (newDir != null) {
            newDir = newDir.toUpperCase();
            // Cegah langsung memutar 180 derajat ke badan sendiri
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

        spawnApple("APL-" + (System.currentTimeMillis() % 10000), random.nextInt(10) > 7 ? "GOLDEN" : "NORMAL", random.nextInt(10) > 7 ? 30 : 10);
        return snake;
    }

    public synchronized Snake toggleSkill(String snakeId, boolean active) {
        Snake snake = getSnake(snakeId);
        snake.setSkillActive(active);
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
     * Menggerakkan semua ular (player & bot) secara terus menerus sehingga game tidak berhenti
     * meskipun tab browser sedang berada di background / ganti tab.
     */
    @Scheduled(fixedRate = 120)
    public synchronized void serverTick() {
        if (snakes.isEmpty()) return;

        for (Snake snake : snakes.values()) {
            if (!snake.isAlive() || snake.getSegments().isEmpty()) continue;

            // AI sederhana untuk Bot agar berbelok secara dinamis
            if (snake.getId().startsWith("BOT") && random.nextInt(10) > 7) {
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
            Point newHead = new Point(nextX, nextY);

            // Cek makan apel
            String eatenAppleId = null;
            for (Apple apple : apples.values()) {
                if (apple.x() == nextX && apple.y() == nextY) {
                    eatenAppleId = apple.id();
                    break;
                }
            }

            // Tambahkan kepala baru
            snake.getSegments().add(0, newHead);

            if (eatenAppleId != null) {
                eatApple(snake.getId(), eatenAppleId);
            } else {
                // Buang buntut jika tidak makan
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
