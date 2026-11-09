package com.snake.arenaservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snake.arenaservice.client.PlayerServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pengujian khusus endpoint GET dan POST pada Arena Service API v1.0.
 * PlayerServiceClient di-mock agar pengujian tidak bergantung pada player-service yang sedang berjalan.
 * Gunakan playerId unik per pengujian dan bersihkan ular melalui DELETE agar state arena (singleton) tetap bersih.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArenaGetPostApiTests {
    private static final String SNAKES = "/api/v1/arena/snakes";
    private static final String STATE = "/api/v1/arena/state";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private PlayerServiceClient playerServiceClient;
    private static String joinPayload(String playerId, String name, String type, String color) {
        String colorPart = color == null ? "" : ",\"color\":\"" + color + "\"";
        return String.format("{\"playerId\":\"%s\",\"playerName\":\"%s\",\"snakeType\":\"%s\"%s}", playerId, name, type, colorPart);
    }
    private JsonNode join(String playerId, String name, String type, String color) throws Exception {
        String res = mockMvc.perform(post(SNAKES).contentType(MediaType.APPLICATION_JSON)
                        .content(joinPayload(playerId, name, type, color)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res);
    }
    private void leave(String snakeId) throws Exception {
        mockMvc.perform(delete(SNAKES + "/" + snakeId));
    }
    // ------------------------------------------------------------------ POST
    @Test
    @DisplayName("POST /api/v1/arena/snakes - 201 Created, serpent di-spawn hidup dengan 3 segmen dan skor 0")
    void postJoinSpawnsAliveSnake() throws Exception {
        JsonNode snake = join("PL-GP-001", "SpawnTester", "VIPER", "#00ffcc");
        try {
            assertTrue(snake.get("id").asText().startsWith("SNK-"));
            assertEquals("PL-GP-001", snake.get("playerId").asText());
            assertEquals("SpawnTester", snake.get("playerName").asText());
            assertEquals("VIPER", snake.get("snakeType").asText());
            assertEquals("#00ffcc", snake.get("color").asText());
            assertEquals(0, snake.get("score").asInt());
            assertTrue(snake.get("alive").asBoolean());
            assertEquals(3, snake.get("segments").size());
        } finally {
            leave(snake.get("id").asText());
        }
    }
    @Test
    @DisplayName("POST /api/v1/arena/snakes - warna dikosongkan, server memakai warna bawaan sesuai kelas")
    void postJoinWithoutColorUsesClassDefault() throws Exception {
        JsonNode snake = join("PL-GP-002", "NoColor", "COBRA", null);
        try {
            assertEquals("#ffd700", snake.get("color").asText());
        } finally {
            leave(snake.get("id").asText());
        }
    }
    @Test
    @DisplayName("POST /api/v1/arena/snakes - nama bentrok dengan pemain lain aktif diberi tag unik #XXXX")
    void postJoinWithClashingNameReceivesTag() throws Exception {
        JsonNode first = join("PL-GP-003A", "ClashName", "VIPER", null);
        JsonNode second = join("PL-GP-003B", "ClashName", "PYTHON", null);
        try {
            assertEquals("ClashName", first.get("playerName").asText());
            assertTrue(second.get("playerName").asText().matches("ClashName#\\d{4}"),
                    "Nama kedua harus berformat ClashName#XXXX tetapi: " + second.get("playerName").asText());
        } finally {
            leave(first.get("id").asText());
            leave(second.get("id").asText());
        }
    }
    @Test
    @DisplayName("POST /api/v1/arena/snakes - 400 Bad Request jika field wajib kosong")
    void postJoinWithMissingFieldsReturnsBadRequest() throws Exception {
        mockMvc.perform(post(SNAKES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snakeType\":\"COBRA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is(SNAKES)))
                .andExpect(jsonPath("$.invalidFields.playerId", notNullValue()))
                .andExpect(jsonPath("$.invalidFields.playerName", notNullValue()));
    }
    @Test
    @DisplayName("POST /api/v1/arena/snakes - 400 Bad Request ketika kapasitas 16 pemain telah penuh")
    void postJoinRejectedWhenArenaIsFull() throws Exception {
        List<String> joined = new ArrayList<>();
        try {
            boolean rejected = false;
            // Jumlah penghuni awal tidak tetap karena bot dapat gugur secara permanen akibat tabrakan,
            // sehingga yang diuji adalah invarian kapasitas: arena menolak POST begitu penuh, tidak pernah melebihi 16.
            for (int i = 0; i < 20 && !rejected; i++) {
                MvcResult result = mockMvc.perform(post(SNAKES).contentType(MediaType.APPLICATION_JSON)
                                .content(joinPayload("PL-FULL-" + i, "Filler" + i, "VIPER", null)))
                        .andReturn();
                int httpStatus = result.getResponse().getStatus();
                if (httpStatus == 201) {
                    joined.add(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
                } else {
                    rejected = true;
                    assertEquals(400, httpStatus);
                    assertTrue(result.getResponse().getContentAsString().contains("Arena penuh"));
                }
            }
            assertTrue(rejected, "Arena seharusnya menolak pemain setelah kapasitas 16 tercapai");
            // Toleransi 1 slot: game loop (120 ms) dapat mengeliminasi satu ular tepat setelah penolakan.
            mockMvc.perform(get(STATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maxPlayers", is(16)))
                    .andExpect(jsonPath("$.activePlayers", allOf(greaterThanOrEqualTo(15), lessThanOrEqualTo(16))));
        } finally {
            joined.forEach(id -> {
                try { leave(id); } catch (Exception ignored) { }
            });
        }
    }
    // ------------------------------------------------------------------- GET
    @Test
    @DisplayName("GET /api/v1/arena/state - 200 OK snapshot memuat dimensi grid, kapasitas, serpent, apel, dan leaderboard")
    void getStateReturnsFullSnapshot() throws Exception {
        JsonNode snake = join("PL-GP-004", "StateWatcher", "PYTHON", "#00d4ff");
        try {
            String id = snake.get("id").asText();
            mockMvc.perform(get(STATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gridWidth", is(30)))
                    .andExpect(jsonPath("$.gridHeight", is(30)))
                    .andExpect(jsonPath("$.maxPlayers", is(16)))
                    .andExpect(jsonPath("$.activePlayers", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.snakes[?(@.id=='" + id + "')].playerName", contains("StateWatcher")))
                    .andExpect(jsonPath("$.apples", not(empty())))
                    .andExpect(jsonPath("$.apples.length()", lessThanOrEqualTo(6)))
                    .andExpect(jsonPath("$.leaderboard[?(@.snakeId=='" + id + "')].score", contains(0)));
        } finally {
            leave(snake.get("id").asText());
        }
    }
    @Test
    @DisplayName("GET /api/v1/arena/snakes/{id} - 200 OK telemetri serpent sama dengan hasil POST")
    void getSnakeByIdReturnsTelemetry() throws Exception {
        JsonNode snake = join("PL-GP-005", "Telemetry", "COBRA", "#ffd700");
        String id = snake.get("id").asText();
        try {
            mockMvc.perform(get(SNAKES + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id)))
                    .andExpect(jsonPath("$.playerId", is("PL-GP-005")))
                    .andExpect(jsonPath("$.snakeType", is("COBRA")))
                    .andExpect(jsonPath("$.alive", is(true)))
                    .andExpect(jsonPath("$.segments", hasSize(greaterThanOrEqualTo(3))))
                    .andExpect(jsonPath("$.skillActive", is(false)));
        } finally {
            leave(id);
        }
    }
    @Test
    @DisplayName("GET /api/v1/arena/snakes/{id} - 404 Not Found untuk serpent yang tidak ada di arena")
    void getUnknownSnakeReturnsNotFound() throws Exception {
        mockMvc.perform(get(SNAKES + "/SNK-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.path", is(SNAKES + "/SNK-99999")))
                .andExpect(jsonPath("$.message", containsString("SNK-99999")));
    }
    @Test
    @DisplayName("GET /v3/api-docs - 200 OK dokumen OpenAPI memuat path GET dan POST arena")
    void getOpenApiDocumentContainsArenaPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/arena/snakes'].post", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/arena/state'].get", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/arena/snakes/{id}'].get", notNullValue()));
    }
}