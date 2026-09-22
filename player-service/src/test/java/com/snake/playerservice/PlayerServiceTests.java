package com.snake.playerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snake.playerservice.v1.dto.PlayerStatsUpdateInput;
import com.snake.playerservice.v1.dto.PlayerV1Input;
import com.snake.playerservice.v2.dto.AbilityInput;
import com.snake.playerservice.v2.dto.ClassProfileInput;
import com.snake.playerservice.v2.dto.PlayerV2Input;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerServiceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Test v1 POST - Berhasil membuat pemain baru (201 Created)")
    void testCreatePlayerV1() throws Exception {
        PlayerV1Input input = new PlayerV1Input("PlayerViper", "VIPER");

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", is("PlayerViper")))
                .andExpect(jsonPath("$.snakeType", is("VIPER")))
                .andExpect(jsonPath("$.speed", is(7)));
    }

    @Test
    @DisplayName("Test v1 GET all, GET by ID, PUT, PATCH, DELETE lifecycle")
    void testV1FullLifecycle() throws Exception {
        // 1. POST
        PlayerV1Input input = new PlayerV1Input("PythonHero", "PYTHON");
        String response = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String playerId = objectMapper.readTree(response).get("id").asText();

        // 2. GET by ID
        mockMvc.perform(get("/api/v1/players/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("PythonHero")));

        // 3. PUT (Update data)
        PlayerV1Input updateInput = new PlayerV1Input("PythonHeroUpdated", "COBRA");
        mockMvc.perform(put("/api/v1/players/" + playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("PythonHeroUpdated")))
                .andExpect(jsonPath("$.snakeType", is("COBRA")));

        // 4. PATCH (Update Stats)
        PlayerStatsUpdateInput statsInput = new PlayerStatsUpdateInput(250, true);
        mockMvc.perform(patch("/api/v1/players/" + playerId + "/stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statsInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highScore", is(250)))
                .andExpect(jsonPath("$.totalMatches", is(1)));

        // 5. DELETE
        mockMvc.perform(delete("/api/v1/players/" + playerId))
                .andExpect(status().isNoContent());

        // 6. GET 404 after delete
        mockMvc.perform(get("/api/v1/players/" + playerId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test v2 POST - Berhasil membuat pemain dengan nested classProfile")
    void testCreatePlayerV2() throws Exception {
        ClassProfileInput classProfile = new ClassProfileInput("COBRA", "GOLDEN_SENSE", new AbilityInput("Poison Spit", 6, 1.5));
        PlayerV2Input input = new PlayerV2Input("CobraKing", classProfile);

        mockMvc.perform(post("/api/v2/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.classProfile.className", is("COBRA")))
                .andExpect(jsonPath("$.classProfile.activeAbility.name", is("Poison Spit")));
    }

    @Test
    @DisplayName("Breaking Changes Test - Payload v1 gagal dieksekusi di endpoint v2 (400 Bad Request)")
    void testBreakingChangeV1PayloadRejectedByV2() throws Exception {
        // Format v1 lama yang flat tanpa classProfile
        String legacyV1Json = "{\"username\": \"LegacyUser\", \"snakeType\": \"VIPER\"}";

        mockMvc.perform(post("/api/v2/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyV1Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    @DisplayName("Test Option C - Pendaftaran nama dasar sama menghasilkan tag unik dan ID berbeda")
    void testDiscriminatorTagSeparation() throws Exception {
        // Player 1 di Device A
        PlayerV1Input p1 = new PlayerV1Input("ViperPilot", "VIPER");
        String res1 = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("ViperPilot")))
                .andReturn().getResponse().getContentAsString();

        String id1 = objectMapper.readTree(res1).get("id").asText();

        // Player 2 di Device B mendaftar dengan nama dasar sama
        PlayerV1Input p2 = new PlayerV1Input("ViperPilot", "VIPER");
        String res2 = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", containsString("ViperPilot#")))
                .andReturn().getResponse().getContentAsString();

        String id2 = objectMapper.readTree(res2).get("id").asText();

        org.junit.jupiter.api.Assertions.assertNotEquals(id1, id2);
    }
}
