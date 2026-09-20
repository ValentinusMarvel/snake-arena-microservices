package com.snake.playerservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "snake_type", nullable = false)
    private String snakeType;

    @Column(name = "speed")
    private int speed;

    @Column(name = "high_score")
    private int highScore;

    @Column(name = "total_matches")
    private int totalMatches;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PlayerEntity() {
    }

    public PlayerEntity(String id, String username, String snakeType, int speed, int highScore, int totalMatches, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.snakeType = snakeType;
        this.speed = speed;
        this.highScore = highScore;
        this.totalMatches = totalMatches;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSnakeType() {
        return snakeType;
    }

    public void setSnakeType(String snakeType) {
        this.snakeType = snakeType;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getHighScore() {
        return highScore;
    }

    public void setHighScore(int highScore) {
        this.highScore = highScore;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
