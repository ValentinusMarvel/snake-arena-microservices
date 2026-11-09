package com.snake.playerservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pengujian khusus endpoint GET dan POST pada Player Service API v1.0.
 * Setiap pengujian bersifat mandiri: data dibuat sendiri melalui POST, tidak bergantung pada urutan eksekusi.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerV1GetPostApiTests {
    private static final String BASE = "/api/v1/players";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    /** Helper: kirim POST registrasi dan kembalikan body respons sebagai JsonNode. */
    private JsonNode register(String username, String snakeType) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"snakeType\":\"%s\"}", username, snakeType);
        String res = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // ID dibangkitkan dari System.currentTimeMillis(); jeda kecil mencegah dua POST beruntun jatuh di milidetik yang sama.
        Thread.sleep(2);
        return objectMapper.readTree(res);
    }
    // ------------------------------------------------------------------ POST
    @ParameterizedTest(name = "POST {0} -> kecepatan dasar {1}")
    @CsvSource({"VIPER,7", "PYTHON,5", "COBRA,6"})
    @DisplayName("POST /api/v1/players - 201 Created dengan kecepatan dasar sesuai tipe serpent")
    void postCreatesPlayerWithSpeedPerSnakeType(String snakeType, int expectedSpeed) throws Exception {
        String username = "Speed" + snakeType;
        String body = String.format("{\"username\":\"%s\",\"snakeType\":\"%s\"}", username, snakeType);
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", startsWith("PL-")))
                .andExpect(jsonPath("$.username", startsWith(username)))
                .andExpect(jsonPath("$.snakeType", is(snakeType)))
                .andExpect(jsonPath("$.speed", is(expectedSpeed)))
                .andExpect(jsonPath("$.highScore", is(0)))
                .andExpect(jsonPath("$.totalMatches", is(0)))
                .andExpect(jsonPath("$.createdAt", not(emptyString())));
    }
    @Test
    @DisplayName("POST /api/v1/players - snakeType huruf kecil dinormalisasi menjadi huruf besar")
    void postNormalizesSnakeTypeToUpperCase() throws Exception {
        JsonNode created = register("LowerCaseTester", "cobra");
        assertEquals("COBRA", created.get("snakeType").asText());
        assertEquals(6, created.get("speed").asInt());
    }
    @Test
    @DisplayName("POST /api/v1/players - username duplikat otomatis diberi tag unik Nama#XXXX")
    void postDuplicateUsernameReceivesFourDigitTag() throws Exception {
        JsonNode first = register("TagTarget", "VIPER");
        JsonNode second = register("TagTarget", "PYTHON");
        assertEquals("TagTarget", first.get("username").asText());
        assertTrue(
                second.get("username").asText().matches("TagTarget#\\d{4}"),
                "Username kedua harus berformat TagTarget#XXXX tetapi: " + second.get("username").asText());
        assertNotEquals(first.get("id").asText(), second.get("id").asText());
    }
    @ParameterizedTest(name = "POST payload tidak valid: {0}")
    @CsvSource(delimiter = '|', value = {
            "username kosong      | {\"username\":\"\",\"snakeType\":\"VIPER\"}                   | username",
            "username terlalu pendek | {\"username\":\"ab\",\"snakeType\":\"VIPER\"}              | username",
            "username terlalu panjang | {\"username\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"snakeType\":\"VIPER\"} | username",
            "snakeType kosong     | {\"username\":\"ValidName\",\"snakeType\":\"\"}               | snakeType",
            "snakeType hilang     | {\"username\":\"ValidName\"}                                  | snakeType"
    })
    @DisplayName("POST /api/v1/players - 400 Bad Request dengan rincian invalidFields")
    void postInvalidPayloadReturnsBadRequest(String skenario, String payload, String invalidField) throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is(BASE)))
                .andExpect(jsonPath("$.invalidFields." + invalidField, notNullValue()));
    }
    // ------------------------------------------------------------------- GET
    @Test
    @DisplayName("GET /api/v1/players/{id} - 200 OK mengembalikan profil yang sama dengan hasil POST")
    void getByIdReturnsPlayerCreatedByPost() throws Exception {
        JsonNode created = register("ReadBackUser", "VIPER");
        String id = created.get("id").asText();
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.username", is("ReadBackUser")))
                .andExpect(jsonPath("$.snakeType", is("VIPER")))
                .andExpect(jsonPath("$.speed", is(7)))
                // POST mengembalikan nilai berpresisi nanodetik, sedangkan GET membaca nilai yang telah dipersist H2 (mikrodetik);
                // perbandingan dilakukan sampai tingkat milidetik (23 karakter pertama format ISO-8601).
                .andExpect(jsonPath("$.createdAt", startsWith(created.get("createdAt").asText().substring(0, 23))));
    }
    @Test
    @DisplayName("GET /api/v1/players/{id} - 404 Not Found untuk ID yang tidak terdaftar")
    void getByUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get(BASE + "/PL-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.path", is(BASE + "/PL-99999")))
                .andExpect(jsonPath("$.message", containsString("PL-99999")));
    }
    @Test
    @DisplayName("GET /api/v1/players - 200 OK daftar memuat pemain yang baru didaftarkan")
    void getAllContainsRegisteredPlayers() throws Exception {
        String idA = register("ListUserAlpha", "VIPER").get("id").asText();
        String idB = register("ListUserBeta", "COBRA").get("id").asText();
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", instanceOf(java.util.List.class)))
                .andExpect(jsonPath("$[?(@.id=='" + idA + "')].username", contains("ListUserAlpha")))
                .andExpect(jsonPath("$[?(@.id=='" + idB + "')].snakeType", contains("COBRA")));
    }
}