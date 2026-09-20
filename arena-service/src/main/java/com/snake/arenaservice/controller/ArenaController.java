package com.snake.arenaservice.controller;

import com.snake.arenaservice.client.PlayerServiceClient;
import com.snake.arenaservice.domain.ArenaState;
import com.snake.arenaservice.domain.Snake;
import com.snake.arenaservice.dto.EatAppleInput;
import com.snake.arenaservice.dto.JoinArenaInput;
import com.snake.arenaservice.dto.LoadoutUpdateInput;
import com.snake.arenaservice.dto.UpdatePositionInput;
import com.snake.arenaservice.service.ArenaManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/arena")
@Tag(name = "arena", description = "API Arena Pertandingan Ular (Gameplay Session)")
public class ArenaController {

    private final ArenaManager arenaManager;
    private final PlayerServiceClient playerServiceClient;

    public ArenaController(ArenaManager arenaManager, PlayerServiceClient playerServiceClient) {
        this.arenaManager = arenaManager;
        this.playerServiceClient = playerServiceClient;
    }

    @PostMapping("/snakes")
    @Operation(summary = "Bergabung ke arena dengan ular baru (POST)", responses = {
            @ApiResponse(responseCode = "201", description = "Ular berhasil masuk ke arena"),
            @ApiResponse(responseCode = "400", description = "Data input tidak valid / Arena penuh")
    })
    public ResponseEntity<Snake> joinArena(@Valid @RequestBody JoinArenaInput input) {
        Snake snake = arenaManager.joinArena(input.playerId(), input.playerName(), input.snakeType(), input.color());
        return ResponseEntity.status(HttpStatus.CREATED).body(snake);
    }

    @GetMapping("/state")
    @Operation(summary = "Mengambil kondisi arena snapshot terkini (GET)", responses = {
            @ApiResponse(responseCode = "200", description = "State arena berhasil diambil")
    })
    public ResponseEntity<ArenaState> getArenaState() {
        return ResponseEntity.ok(arenaManager.getArenaState());
    }

    @GetMapping("/snakes/{id}")
    @Operation(summary = "Mengambil detail ular di arena berdasarkan ID (GET)", responses = {
            @ApiResponse(responseCode = "200", description = "Data ular ditemukan"),
            @ApiResponse(responseCode = "404", description = "Ular tidak ada di arena")
    })
    public ResponseEntity<Snake> getSnakeById(@PathVariable String id) {
        return ResponseEntity.ok(arenaManager.getSnake(id));
    }

    @PatchMapping("/snakes/{id}/position")
    @Operation(summary = "Sinkronisasi posisi segmen ular secara real-time (PATCH)", responses = {
            @ApiResponse(responseCode = "200", description = "Posisi ular berhasil diperbarui"),
            @ApiResponse(responseCode = "404", description = "Ular tidak ditemukan")
    })
    public ResponseEntity<Snake> updatePosition(@PathVariable String id, @RequestBody UpdatePositionInput input) {
        Snake updated = arenaManager.updatePosition(id, input.segments(), input.direction());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/snakes/{id}/loadout")
    @Operation(summary = "Mengganti loadout / skin ular di arena (PUT)", responses = {
            @ApiResponse(responseCode = "200", description = "Loadout berhasil diubah"),
            @ApiResponse(responseCode = "404", description = "Ular tidak ditemukan")
    })
    public ResponseEntity<Snake> updateLoadout(@PathVariable String id, @Valid @RequestBody LoadoutUpdateInput input) {
        Snake updated = arenaManager.updateLoadout(id, input.snakeType(), input.color());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/snakes/{id}/eat")
    @Operation(summary = "Mengkonsumsi apel di arena (PATCH)", responses = {
            @ApiResponse(responseCode = "200", description = "Apel dimakan, skor dan panjang bertambah"),
            @ApiResponse(responseCode = "404", description = "Ular tidak ditemukan")
    })
    public ResponseEntity<Snake> eatApple(@PathVariable String id, @Valid @RequestBody EatAppleInput input) {
        Snake updated = arenaManager.eatApple(id, input.appleId());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/snakes/{id}/skill")
    @Operation(summary = "Mengaktifkan/mematikan skill kelas ular (PATCH)", responses = {
            @ApiResponse(responseCode = "200", description = "Status skill diperbarui")
    })
    public ResponseEntity<Snake> toggleSkill(@PathVariable String id, @RequestParam boolean active) {
        Snake updated = arenaManager.toggleSkill(id, active);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/snakes/{id}")
    @Operation(summary = "Ular tereliminasi / keluar dari arena (DELETE) -> Memicu sinkronisasi skor ke Player Service", responses = {
            @ApiResponse(responseCode = "204", description = "Ular berhasil keluar dan skor tersinkronisasi"),
            @ApiResponse(responseCode = "404", description = "Ular tidak ditemukan")
    })
    public ResponseEntity<Void> eliminateSnake(@PathVariable String id) {
        Snake eliminated = arenaManager.eliminateSnake(id);
        playerServiceClient.syncPlayerScore(eliminated.getPlayerId(), eliminated.getScore(), true);
        return ResponseEntity.noContent().build();
    }
}
