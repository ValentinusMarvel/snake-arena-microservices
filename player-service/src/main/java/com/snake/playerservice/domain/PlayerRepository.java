package com.snake.playerservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<PlayerEntity, String> {
    Optional<PlayerEntity> findByUsername(String username);
}
