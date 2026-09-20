package com.snake.playerservice.v1.controller;

import com.snake.playerservice.domain.PlayerEntity;
import com.snake.playerservice.domain.PlayerRepository;
import com.snake.playerservice.exception.EntityNotFoundException;
import com.snake.playerservice.v1.dto.PlayerStatsUpdateInput;
import com.snake.playerservice.v1.dto.PlayerV1Input;
import com.snake.playerservice.v1.dto.PlayerV1Output;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "v1-players", description = "API Profil Pemain Versi 1 (Legacy Flat Contract)")
public class PlayerV1Controller {

    private final PlayerRepository repository;

    public PlayerV1Controller(PlayerRepository repository) {
        this.repository = repository;
    }

    private int calculateSpeed(String snakeType) {
        if ("VIPER".equalsIgnoreCase(snakeType)) return 7;
        if ("PYTHON".equalsIgnoreCase(snakeType)) return 5;
        if ("COBRA".equalsIgnoreCase(snakeType)) return 6;
        return 5;
    }

    private PlayerV1Output toOutput(PlayerEntity entity) {
        return new PlayerV1Output(
                entity.getId(),
                entity.getUsername(),
                entity.getSnakeType(),
                entity.getSpeed(),
                entity.getHighScore(),
                entity.getTotalMatches(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : ""
        );
    }

    @PostMapping
    @Operation(summary = "Mendaftar player baru (v1 POST)", responses = {
            @ApiResponse(responseCode = "201", description = "Pemain berhasil didaftarkan"),
            @ApiResponse(responseCode = "400", description = "Username atau tipe ular tidak valid")
    })
    public ResponseEntity<PlayerV1Output> createPlayer(@Valid @RequestBody PlayerV1Input input) {
        repository.findByUsername(input.username()).ifPresent(p -> {
            throw new IllegalArgumentException("Username '" + input.username() + "' sudah digunakan");
        });

        String id = "PL-" + (System.currentTimeMillis() % 100000);
        int speed = calculateSpeed(input.snakeType());
        PlayerEntity entity = new PlayerEntity(id, input.username(), input.snakeType().toUpperCase(), speed, 0, 0, LocalDateTime.now());
        PlayerEntity saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOutput(saved));
    }

    @GetMapping
    @Operation(summary = "Mengambil semua daftar pemain (v1 GET)", responses = {
            @ApiResponse(responseCode = "200", description = "Daftar pemain berhasil diambil")
    })
    public ResponseEntity<List<PlayerV1Output>> getAllPlayers() {
        List<PlayerV1Output> list = repository.findAll().stream().map(this::toOutput).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Mengambil detail pemain berdasarkan ID (v1 GET)", responses = {
            @ApiResponse(responseCode = "200", description = "Pemain ditemukan"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<PlayerV1Output> getPlayerById(@PathVariable String id) {
        PlayerEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan"));
        return ResponseEntity.ok(toOutput(entity));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mengubah / replace data pemain seutuhnya (v1 PUT)", responses = {
            @ApiResponse(responseCode = "200", description = "Pemain berhasil diperbarui"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<PlayerV1Output> updatePlayer(@PathVariable String id, @Valid @RequestBody PlayerV1Input input) {
        PlayerEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan"));
        entity.setUsername(input.username());
        entity.setSnakeType(input.snakeType().toUpperCase());
        entity.setSpeed(calculateSpeed(input.snakeType()));
        PlayerEntity updated = repository.save(entity);
        return ResponseEntity.ok(toOutput(updated));
    }

    @PatchMapping("/{id}/stats")
    @Operation(summary = "Modifikasi parsial skor dan jumlah match (v1 PATCH)", responses = {
            @ApiResponse(responseCode = "200", description = "Skor berhasil diperbarui"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<PlayerV1Output> updateStats(@PathVariable String id, @Valid @RequestBody PlayerStatsUpdateInput input) {
        PlayerEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan"));
        if (input.score() > entity.getHighScore()) {
            entity.setHighScore(input.score());
        }
        if (input.matchCompleted()) {
            entity.setTotalMatches(entity.getTotalMatches() + 1);
        }
        PlayerEntity updated = repository.save(entity);
        return ResponseEntity.ok(toOutput(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Menghapus akun pemain (v1 DELETE)", responses = {
            @ApiResponse(responseCode = "204", description = "Pemain berhasil dihapus"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<Void> deletePlayer(@PathVariable String id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
