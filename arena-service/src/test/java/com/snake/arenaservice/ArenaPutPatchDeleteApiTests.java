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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pengujian khusus endpoint PUT, PATCH, dan DELETE pada Arena Service API v1.0.
 * PlayerServiceClient di-mock sehingga sinkronisasi skor pada DELETE dapat diverifikasi tanpa player-service.
 * Setiap pengujian memakai playerId unik dan membersihkan serpent miliknya pada blok finally.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArenaPutPatchDeleteApiTests {

    private static final String SNAKES = "/api/v1/arena/snakes";
    private static final String STATE = "/api/v1/arena/state";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlayerServiceClient playerServiceClient;

    private JsonNode join(String playerId, String name, String type) throws Exception {
        String body = String.format("{\"playerId\":\"%s\",\"playerName\":\"%s\",\"snakeType\":\"%s\"}", playerId, name, type);
        String res = mockMvc.perform(post(SNAKES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res);
    }

    private void leave(String snakeId) throws Exception {
        mockMvc.perform(delete(SNAKES + "/" + snakeId));
    }

    private static String opposite(String dir) {
        return switch (dir) {
            case "UP" -> "DOWN";
            case "DOWN" -> "UP";
            case "LEFT" -> "RIGHT";
            default -> "LEFT";
        };
    }

    // ------------------------------------------------------------------- PUT

    @Test
    @DisplayName("PUT /snakes/{id}/loadout - 200 OK mengganti kelas dan warna serpent di arena")
    void putLoadoutReplacesTypeAndColor() throws Exception {
        String id = join("PL-PPD-001", "LoadoutTester", "VIPER").get("id").asText();
        try {
            mockMvc.perform(put(SNAKES + "/" + id + "/loadout").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"snakeType\":\"cobra\",\"color\":\"#ff00aa\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id)))
                    .andExpect(jsonPath("$.snakeType", is("COBRA")))
                    .andExpect(jsonPath("$.color", is("#ff00aa")))
                    .andExpect(jsonPath("$.playerName", is("LoadoutTester")));
            mockMvc.perform(get(SNAKES + "/" + id))
                    .andExpect(jsonPath("$.snakeType", is("COBRA")))
                    .andExpect(jsonPath("$.color", is("#ff00aa")));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PUT /snakes/{id}/loadout - tanpa color, warna direset ke warna bawaan kelas baru")
    void putLoadoutWithoutColorResetsToClassDefault() throws Exception {
        String id = join("PL-PPD-002", "ColorReset", "VIPER").get("id").asText();
        try {
            mockMvc.perform(put(SNAKES + "/" + id + "/loadout").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"snakeType\":\"PYTHON\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.snakeType", is("PYTHON")))
                    .andExpect(jsonPath("$.color", is("#00d4ff")));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PUT /snakes/{id}/loadout - 400 Bad Request jika snakeType kosong")
    void putLoadoutWithoutSnakeTypeReturnsBadRequest() throws Exception {
        String id = join("PL-PPD-003", "LoadoutInvalid", "VIPER").get("id").asText();
        try {
            mockMvc.perform(put(SNAKES + "/" + id + "/loadout").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"color\":\"#123456\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.invalidFields.snakeType", notNullValue()));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PUT /snakes/{id}/loadout - 404 Not Found untuk serpent yang tidak ada")
    void putLoadoutUnknownSnakeReturnsNotFound() throws Exception {
        mockMvc.perform(put(SNAKES + "/SNK-99999/loadout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snakeType\":\"COBRA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("SNK-99999")));
    }

    // ----------------------------------------------------------------- PATCH

    @Test
    @DisplayName("PATCH /snakes/{id}/direction - arah tegak lurus diterima, arah berlawanan diabaikan")
    void patchDirectionAcceptsTurnAndIgnoresReverse() throws Exception {
        String id = join("PL-PPD-004", "Steering", "VIPER").get("id").asText();
        try {
            mockMvc.perform(patch(SNAKES + "/" + id + "/direction").param("dir", "left"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.direction", is("LEFT")));
            // Berbalik 180 derajat ditolak secara diam-diam agar serpent tidak menabrak badannya sendiri.
            mockMvc.perform(patch(SNAKES + "/" + id + "/direction").param("dir", opposite("LEFT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.direction", is("LEFT")));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PATCH /snakes/{id}/position - 200 OK segmen dan arah disinkronkan sesuai payload klien")
    void patchPositionSynchronisesSegments() throws Exception {
        String id = join("PL-PPD-005", "PositionSync", "PYTHON").get("id").asText();
        try {
            String body = "{\"segments\":[{\"x\":2,\"y\":2},{\"x\":2,\"y\":3},{\"x\":2,\"y\":4},{\"x\":2,\"y\":5}],"
                    + "\"direction\":\"up\"}";
            mockMvc.perform(patch(SNAKES + "/" + id + "/position").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.direction", is("UP")))
                    .andExpect(jsonPath("$.segments", hasSize(4)))
                    .andExpect(jsonPath("$.segments[3].x", is(2)))
                    .andExpect(jsonPath("$.segments[3].y", is(5)));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PATCH /snakes/{id}/eat - apel dikonsumsi: skor bertambah, panjang +1, apel hilang dari arena")
    void patchEatAppleAddsScoreAndGrowsSnake() throws Exception {
        JsonNode snake = join("PL-PPD-006", "AppleEater", "VIPER");
        String id = snake.get("id").asText();
        try {
            JsonNode state = objectMapper.readTree(mockMvc.perform(get(STATE))
                    .andReturn().getResponse().getContentAsString());
            JsonNode apple = state.get("apples").get(0);
            String appleId = apple.get("id").asText();
            int points = apple.get("points").asInt();
            int lengthBefore = objectMapper.readTree(mockMvc.perform(get(SNAKES + "/" + id))
                    .andReturn().getResponse().getContentAsString()).get("segments").size();

            JsonNode eaten = objectMapper.readTree(mockMvc.perform(patch(SNAKES + "/" + id + "/eat")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"appleId\":\"" + appleId + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertTrue(eaten.get("score").asInt() >= points, "Skor harus bertambah sebesar nilai apel");
            assertEquals(lengthBefore + 1, eaten.get("segments").size());
            mockMvc.perform(get(STATE))
                    .andExpect(jsonPath("$.apples[?(@.id=='" + appleId + "')]", empty()));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PATCH /snakes/{id}/eat - 400 Bad Request jika appleId kosong")
    void patchEatWithoutAppleIdReturnsBadRequest() throws Exception {
        String id = join("PL-PPD-007", "EatInvalid", "COBRA").get("id").asText();
        try {
            mockMvc.perform(patch(SNAKES + "/" + id + "/eat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidFields.appleId", notNullValue()));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PATCH /snakes/{id}/skill - aktivasi berhasil, aktivasi ulang saat cooldown ditolak 400")
    void patchSkillActivatesThenRejectsDuringCooldown() throws Exception {
        String id = join("PL-PPD-008", "SkillUser", "PYTHON").get("id").asText();
        try {
            mockMvc.perform(patch(SNAKES + "/" + id + "/skill").param("active", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skillActive", is(true)))
                    .andExpect(jsonPath("$.skillCooldownUntil", greaterThan(0L)));
            mockMvc.perform(patch(SNAKES + "/" + id + "/skill").param("active", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skillActive", is(false)));
            mockMvc.perform(patch(SNAKES + "/" + id + "/skill").param("active", "true"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("cooldown")));
        } finally {
            leave(id);
        }
    }

    @Test
    @DisplayName("PATCH /snakes/{id}/heartbeat - 200 OK; PATCH ke serpent yang tidak ada - 404 Not Found")
    void patchHeartbeatAndUnknownSnake() throws Exception {
        String id = join("PL-PPD-009", "Heartbeat", "VIPER").get("id").asText();
        try {
            mockMvc.perform(patch(SNAKES + "/" + id + "/heartbeat"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id)))
                    .andExpect(jsonPath("$.alive", is(true)));
        } finally {
            leave(id);
        }
        mockMvc.perform(patch(SNAKES + "/SNK-99999/direction").param("dir", "UP"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    @DisplayName("DELETE /snakes/{id} - 204 No Content, serpent keluar arena dan skor disinkronkan ke player-service")
    void deleteEliminatesSnakeAndSyncsScore() throws Exception {
        String id = join("PL-PPD-010", "Eliminated", "COBRA").get("id").asText();
        mockMvc.perform(patch(SNAKES + "/" + id + "/eat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appleId\":\"APL-TIDAK-ADA\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete(SNAKES + "/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(playerServiceClient).syncPlayerScore(eq("PL-PPD-010"), intThat(s -> s >= 10), eq(true));
        mockMvc.perform(get(SNAKES + "/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get(STATE))
                .andExpect(jsonPath("$.snakes[?(@.id=='" + id + "')]", empty()))
                .andExpect(jsonPath("$.leaderboard[?(@.snakeId=='" + id + "')]", empty()));
    }

    @Test
    @DisplayName("DELETE /snakes/{id} - 404 Not Found untuk serpent yang tidak ada, tanpa sinkronisasi skor")
    void deleteUnknownSnakeReturnsNotFoundWithoutSync() throws Exception {
        mockMvc.perform(delete(SNAKES + "/SNK-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is(SNAKES + "/SNK-99999")))
                .andExpect(jsonPath("$.message", containsString("SNK-99999")));
        verify(playerServiceClient, never()).syncPlayerScore(eq("SNK-99999"), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("GET /v3/api-docs - 200 OK dokumen OpenAPI memuat operasi PUT, PATCH, dan DELETE arena")
    void openApiDocumentContainsMutationPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/arena/snakes/{id}/loadout'].put", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/arena/snakes/{id}/direction'].patch", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/arena/snakes/{id}/eat'].patch", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/arena/snakes/{id}'].delete", notNullValue()));
    }
}
