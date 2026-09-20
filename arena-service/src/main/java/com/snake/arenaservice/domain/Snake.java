package com.snake.arenaservice.domain;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private String id;
    private String playerId;
    private String playerName;
    private String snakeType;
    private String color;
    private List<Point> segments = new ArrayList<>();
    private String direction;
    private int score;
    private boolean alive;
    private boolean skillActive;
    private volatile long lastHeartbeatTime = System.currentTimeMillis();

    public Snake() {}

    public Snake(String id, String playerId, String playerName, String snakeType, String color, List<Point> segments, String direction, int score, boolean alive, boolean skillActive) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.snakeType = snakeType;
        this.color = color;
        this.segments = segments != null ? segments : new ArrayList<>();
        this.direction = direction;
        this.score = score;
        this.alive = alive;
        this.skillActive = skillActive;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getSnakeType() { return snakeType; }
    public void setSnakeType(String snakeType) { this.snakeType = snakeType; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public List<Point> getSegments() { return segments; }
    public void setSegments(List<Point> segments) { this.segments = segments; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public boolean isSkillActive() { return skillActive; }
    public void setSkillActive(boolean skillActive) { this.skillActive = skillActive; }
    public long getLastHeartbeatTime() { return lastHeartbeatTime; }
    public void setLastHeartbeatTime(long lastHeartbeatTime) { this.lastHeartbeatTime = lastHeartbeatTime; }
    public void updateHeartbeat() { this.lastHeartbeatTime = System.currentTimeMillis(); }
}
