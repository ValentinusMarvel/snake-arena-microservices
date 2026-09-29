package com.snake.arenaservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snake.arenaservice.client.PlayerServiceClient;
import com.snake.arenaservice.dto.EatAppleInput;
import com.snake.arenaservice.dto.JoinArenaInput;
import com.snake.arenaservice.dto.LoadoutUpdateInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ArenaServiceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlayerServiceClient playerServiceClient;

    @Test
    @DisplayName("Test Arena Full Lifecycle: Join (POST), State (GET), Loadout (PUT), Eat (PATCH), Leave (DELETE)")
    void testArenaLifecycle() throws Exception {
        doNothing().when(playerServiceClient).syncPlayerScore(anyString(), anyInt(), anyBoolean());

        // 1. POST join arena
        JoinArenaInput joinInput = new JoinArenaInput("PL-101", "PlayerOne", "VIPER", "#00ff88");
        String joinRes = mockMvc.perform(post("/api/v1/arena/snakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinInput)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.playerName", is("PlayerOne")))
                .andExpect(jsonPath("$.snakeType", is("VIPER")))
                .andReturn().getResponse().getContentAsString();

        String snakeId = objectMapper.readTree(joinRes).get("id").asText();

        // 2. GET state
        mockMvc.perform(get("/api/v1/arena/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gridWidth", is(30)))
                .andExpect(jsonPath("$.snakes", not(empty())))
                .andExpect(jsonPath("$.apples", not(empty())));

        // 3. GET snake by ID
        mockMvc.perform(get("/api/v1/arena/snakes/" + snakeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName", is("PlayerOne")));

        // 4. PUT loadout
        LoadoutUpdateInput loadout = new LoadoutUpdateInput("PYTHON", "#00d4ff");
        mockMvc.perform(put("/api/v1/arena/snakes/" + snakeId + "/loadout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loadout)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snakeType", is("PYTHON")));

        // 5. PATCH eat apple
        EatAppleInput eatInput = new EatAppleInput("APL-1");
        mockMvc.perform(patch("/api/v1/arena/snakes/" + snakeId + "/eat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eatInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", greaterThanOrEqualTo(10)));

        // 6. DELETE leave arena & verify inter-service call
        mockMvc.perform(delete("/api/v1/arena/snakes/" + snakeId))
                .andExpect(status().isNoContent());

        verify(playerServiceClient).syncPlayerScore(anyString(), anyInt(), anyBoolean());

        // 7. GET 404 after eliminated
        mockMvc.perform(get("/api/v1/arena/snakes/" + snakeId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test Join Arena Validation Rejection (400 Bad Request)")
    void testJoinArenaValidation() throws Exception {
        // Missing required playerId and playerName
        String invalidPayload = "{\"snakeType\": \"COBRA\"}";

        mockMvc.perform(post("/api/v1/arena/snakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", notNullValue()));
    }

    @Test
    @DisplayName("Test Toggle Skill Endpoint (PATCH)")
    void testToggleSkill() throws Exception {
        JoinArenaInput joinInput = new JoinArenaInput("PL-202", "Alex", "COBRA", "#ffd700");
        String joinRes = mockMvc.perform(post("/api/v1/arena/snakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinInput)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String snakeId = objectMapper.readTree(joinRes).get("id").asText();

        mockMvc.perform(patch("/api/v1/arena/snakes/" + snakeId + "/skill")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillActive", is(true)));
    }

    @Test
    @DisplayName("Test OpenAPI 3 Documentation Endpoint (GET)")
    void testOpenApiEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", notNullValue()))
                .andExpect(jsonPath("$.info.title", containsString("Snake Arena")));
    }

    @Test
    @DisplayName("Test In-Game POST Retains Skill Active & Cooldown (Anti-Exploit)")
    void testPreserveSkillCooldownOnIngamePost() throws Exception {
        JoinArenaInput joinInput = new JoinArenaInput("PL-303", "Hunter", "VIPER", "#00ff88");
        String joinRes = mockMvc.perform(post("/api/v1/arena/snakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinInput)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstSnakeId = objectMapper.readTree(joinRes).get("id").asText();

        // 1. Activate skill
        mockMvc.perform(patch("/api/v1/arena/snakes/" + firstSnakeId + "/skill")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillActive", is(true)));

        // 2. Perform In-Game POST with the same playerId
        String secondJoinRes = mockMvc.perform(post("/api/v1/arena/snakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinInput)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skillActive", is(true)))
                .andReturn().getResponse().getContentAsString();

        String secondSnakeId = objectMapper.readTree(secondJoinRes).get("id").asText();

        // 3. Verify that the skill is still active on the new snake session
        mockMvc.perform(get("/api/v1/arena/snakes/" + secondSnakeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillActive", is(true)))
                .andExpect(jsonPath("$.skillCooldownUntil", greaterThan(0L)));
    }
}
