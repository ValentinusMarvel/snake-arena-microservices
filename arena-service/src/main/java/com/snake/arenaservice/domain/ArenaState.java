package com.snake.arenaservice.domain;

import java.util.List;

public record ArenaState(
        int gridWidth,
        int gridHeight,
        int maxPlayers,
        int activePlayers,
        List<Snake> snakes,
        List<Apple> apples,
        List<LeaderboardEntry> leaderboard
) {}
