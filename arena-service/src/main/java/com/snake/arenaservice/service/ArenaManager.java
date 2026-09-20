package com.snake.arenaservice.service;

import com.snake.arenaservice.domain.*;
import com.snake.arenaservice.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArenaManager {

    private final int gridWidth = 30;
    private final int gridHeight = 30;
    private final Map<String, Snake> snakes = new ConcurrentHashMap<>();
    private final Map<String, Apple> apples = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ArenaManager() {
        // Inisialisasi apel awal di arena
        spawnApple("APL-1", "NORMAL", 10);
        spawnApple("APL-2", "NORMAL", 10);
        spawnApple("APL-3", "GOLDEN", 30);
        spawnApple("APL-4", "NORMAL", 10);

        // Inisialisasi bot simulasi agar arena hidup
        joinArena("BOT-01", "Bot-Viper", "VIPER", "#00ff88", 5, 5);
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
        String snakeId = "SNK-" + (System.currentTimeMillis() % 100000) + "-" + random.nextInt(100);
        List<Point> segments = new ArrayList<>();
        segments.add(new Point(startX, startY));
        segments.add(new Point(startX, startY + 1));
        segments.add(new Point(startX, startY + 2));

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
        if ("VIPER".equalsIgnoreCase(type)) return "#00ff88"; // neon green
        if ("PYTHON".equalsIgnoreCase(type)) return "#00d4ff"; // neon cyan
        if ("COBRA".equalsIgnoreCase(type)) return "#ffd700"; // neon gold
        return "#ffffff";
    }

    public ArenaState getArenaState() {
        List<LeaderboardEntry> leaderboard = snakes.values().stream()
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(s -> new LeaderboardEntry(s.getId(), s.getPlayerName(), s.getSnakeType(), s.getScore()))
                .toList();

        return new ArenaState(gridWidth, gridHeight, new ArrayList<>(snakes.values()), new ArrayList<>(apples.values()), leaderboard);
    }

    public Snake getSnake(String snakeId) {
        Snake snake = snakes.get(snakeId);
        if (snake == null) {
            throw new ResourceNotFoundException("Ular dengan ID '" + snakeId + "' tidak ada di arena");
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

        // Skill Cobra: Bonus poin apel emas
        if ("COBRA".equalsIgnoreCase(snake.getSnakeType()) && apple != null && "GOLDEN".equals(apple.type())) {
            pointsGained += 20;
        }

        snake.setScore(snake.getScore() + pointsGained);

        // Pertambahan panjang ular
        if (!snake.getSegments().isEmpty()) {
            Point tail = snake.getSegments().get(snake.getSegments().size() - 1);
            snake.getSegments().add(new Point(tail.x(), tail.y()));
        }

        // Spawn apel baru untuk menjaga ekosistem arena
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
}
