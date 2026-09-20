package com.snake.arenaservice.dto;

import com.snake.arenaservice.domain.Point;
import java.util.List;

public record UpdatePositionInput(
        List<Point> segments,
        String direction
) {}
