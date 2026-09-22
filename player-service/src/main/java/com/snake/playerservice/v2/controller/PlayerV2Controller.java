package com.snake.playerservice.v2.controller;

import com.snake.playerservice.domain.PlayerEntity;
import com.snake.playerservice.domain.PlayerRepository;
import com.snake.playerservice.exception.EntityNotFoundException;
import com.snake.playerservice.v1.dto.PlayerStatsUpdateInput;
import com.snake.playerservice.v2.dto.AbilityInput;
import com.snake.playerservice.v2.dto.ClassProfileInput;
import com.snake.playerservice.v2.dto.PlayerV2Input;
import com.snake.playerservice.v2.dto.PlayerV2Output;
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
@RequestMapping("/api/v2/players")
@Tag(name = "v2-players", description = "API Profil Pemain Versi 2 (Modern RPG - Breaking Changes Contract)")
public class PlayerV2Controller {

    private final PlayerRepository repository;

    public PlayerV2Controller(PlayerRepository repository) {
        this.repository = repository;
    }

    private ClassProfileInput buildClassProfile(String snakeType) {
        if ("VIPER".equalsIgnoreCase(snakeType)) {
            return new ClassProfileInput("VIPER", "POISON_TRAIL", new AbilityInput("Venom Dash", 5, 1.8));
        } else if ("PYTHON".equalsIgnoreCase(snakeType)) {
            return new ClassProfileInput("PYTHON", "THICK_BODY", new AbilityInput("Apple Magnet", 7, 1.3));
        } else {
            return new ClassProfileInput("COBRA", "GOLDEN_SENSE", new AbilityInput("Poison Spit", 6, 1.5));
        }
    }

    private String calculateTier(int highScore) {
        if (highScore >= 300) return "Master";
        if (highScore >= 150) return "Gold";
        if (highScore >= 50) return "Silver";
        return "Bronze";
    }

    private PlayerV2Output toOutputV2(PlayerEntity entity) {
        return new PlayerV2Output(
                entity.getId(),
                entity.getUsername(),
                buildClassProfile(entity.getSnakeType()),
                entity.getHighScore(),
                entity.getTotalMatches(),
                calculateTier(entity.getHighScore()),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : ""
        );
    }

    @PostMapping
    @Operation(summary = "Mendaftar player baru v2 (Breaking Change: wajib classProfile)", responses = {
            @ApiResponse(responseCode = "201", description = "Pemain v2 berhasil dibuat"),
            @ApiResponse(responseCode = "400", description = "Payload v1 ditolak atau input tidak valid")
    })
    public ResponseEntity<PlayerV2Output> createPlayerV2(@Valid @RequestBody PlayerV2Input input) {
        repository.findByUsername(input.username()).ifPresent(p -> {
            throw new IllegalArgumentException("Username '" + input.username() + "' sudah digunakan");
        });

        String id = "PL-" + (System.currentTimeMillis() % 100000);
        String snakeType = input.classProfile().className().toUpperCase();
        PlayerEntity entity = new PlayerEntity(id, input.username(), snakeType, 6, 0, 0, LocalDateTime.now());
        PlayerEntity saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOutputV2(saved));
    }

    @GetMapping
    @Operation(summary = "Mengambil seluruh pemain format v2", responses = {
            @ApiResponse(responseCode = "200", description = "Daftar pemain v2 berhasil diambil")
    })
    public ResponseEntity<List<PlayerV2Output>> getAllPlayersV2() {
        List<PlayerV2Output> list = repository.findAll().stream().map(this::toOutputV2).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Mengambil detail pemain v2 berdasarkan ID", responses = {
            @ApiResponse(responseCode = "200", description = "Pemain v2 ditemukan"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<PlayerV2Output> getPlayerByIdV2(@PathVariable String id) {
        PlayerEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan"));
        return ResponseEntity.ok(toOutputV2(entity));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mengubah data pemain v2 seutuhnya", responses = {
            @ApiResponse(responseCode = "200", description = "Pemain berhasil diperbarui"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<PlayerV2Output> updatePlayerV2(@PathVariable String id, @Valid @RequestBody PlayerV2Input input) {
        PlayerEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan"));
        repository.findByUsername(input.username()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalArgumentException("Username '" + input.username() + "' sudah digunakan oleh pemain lain");
            }
        });
        entity.setUsername(input.username());
        entity.setSnakeType(input.classProfile().className().toUpperCase());
        PlayerEntity updated = repository.save(entity);
        return ResponseEntity.ok(toOutputV2(updated));
    }

    @PatchMapping("/{id}/stats")
    @Operation(summary = "Modifikasi parsial skor dan jumlah match v2 (v2 PATCH)", responses = {
            @ApiResponse(responseCode = "200", description = "Skor berhasil diperbarui"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<PlayerV2Output> updateStatsV2(@PathVariable String id, @Valid @RequestBody PlayerStatsUpdateInput input) {
        PlayerEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan"));
        if (input.score() > entity.getHighScore()) {
            entity.setHighScore(input.score());
        }
        if (input.matchCompleted()) {
            entity.setTotalMatches(entity.getTotalMatches() + 1);
        }
        PlayerEntity updated = repository.save(entity);
        return ResponseEntity.ok(toOutputV2(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Menghapus akun pemain v2", responses = {
            @ApiResponse(responseCode = "204", description = "Pemain berhasil dihapus"),
            @ApiResponse(responseCode = "404", description = "Pemain tidak ditemukan")
    })
    public ResponseEntity<Void> deletePlayerV2(@PathVariable String id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Pemain dengan ID '" + id + "' tidak ditemukan");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
