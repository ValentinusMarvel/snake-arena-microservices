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
 * Pengujian khusus endpoint PUT, PATCH, dan DELETE pada Player Service API v1.0.
 * Setiap pengujian membuat pemainnya sendiri melalui POST lalu memverifikasi efek perubahan melalui GET.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerV1PutPatchDeleteApiTests {

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

    private static String playerJson(String username, String snakeType) {
        return String.format("{\"username\":\"%s\",\"snakeType\":\"%s\"}", username, snakeType);
    }

    private static String statsJson(int score, boolean matchCompleted) {
        return String.format("{\"score\":%d,\"matchCompleted\":%b}", score, matchCompleted);
    }

    // ------------------------------------------------------------------- PUT

    @Test
    @DisplayName("PUT /api/v1/players/{id} - 200 OK mengganti username dan kelas, speed dihitung ulang, statistik dipertahankan")
    void putReplacesProfileAndRecalculatesSpeed() throws Exception {
        JsonNode created = register("PutOrigin", "VIPER");
        String id = created.get("id").asText();
        mockMvc.perform(patch(BASE + "/" + id + "/stats").contentType(MediaType.APPLICATION_JSON).content(statsJson(80, true)))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(playerJson("PutRenamed", "python")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.username", is("PutRenamed")))
                .andExpect(jsonPath("$.snakeType", is("PYTHON")))
                .andExpect(jsonPath("$.speed", is(5)))
                .andExpect(jsonPath("$.highScore", is(80)))
                .andExpect(jsonPath("$.totalMatches", is(1)));

        // Efek PUT harus persisten dan terbaca kembali melalui GET.
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("PutRenamed")))
                .andExpect(jsonPath("$.snakeType", is("PYTHON")));
    }

    @Test
    @DisplayName("PUT /api/v1/players/{id} - 200 OK bersifat idempoten ketika payload yang sama dikirim berulang")
    void putIsIdempotent() throws Exception {
        String id = register("IdemUser", "COBRA").get("id").asText();
        String payload = playerJson("IdemUserNew", "VIPER");
        String first = mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(objectMapper.readTree(first), objectMapper.readTree(second));
    }

    @Test
    @DisplayName("PUT /api/v1/players/{id} - 400 Bad Request jika username sudah dipakai pemain lain")
    void putWithUsernameOwnedByOtherPlayerReturnsBadRequest() throws Exception {
        register("TakenName", "VIPER");
        String id = register("WantsTakenName", "PYTHON").get("id").asText();
        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(playerJson("TakenName", "PYTHON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("sudah digunakan")));
        // Data pemain tidak boleh berubah setelah permintaan ditolak.
        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(jsonPath("$.username", is("WantsTakenName")));
    }

    @ParameterizedTest(name = "PUT payload tidak valid: {0}")
    @CsvSource(delimiter = '|', value = {
            "username terlalu pendek | {\"username\":\"ab\",\"snakeType\":\"VIPER\"} | username",
            "snakeType kosong        | {\"username\":\"ValidName\",\"snakeType\":\"\"} | snakeType"
    })
    @DisplayName("PUT /api/v1/players/{id} - 400 Bad Request dengan rincian invalidFields")
    void putInvalidPayloadReturnsBadRequest(String skenario, String payload, String invalidField) throws Exception {
        String id = register("PutValidation", "VIPER").get("id").asText();
        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.invalidFields." + invalidField, notNullValue()));
    }

    @Test
    @DisplayName("PUT /api/v1/players/{id} - 404 Not Found untuk ID yang tidak terdaftar")
    void putUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(put(BASE + "/PL-99999").contentType(MediaType.APPLICATION_JSON).content(playerJson("Ghost", "VIPER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("PL-99999")));
    }

    // ----------------------------------------------------------------- PATCH

    @Test
    @DisplayName("PATCH /api/v1/players/{id}/stats - highScore hanya naik jika skor baru lebih tinggi, totalMatches bertambah")
    void patchStatsKeepsHighestScoreAndCountsMatches() throws Exception {
        String id = register("StatsUser", "COBRA").get("id").asText();
        String path = BASE + "/" + id + "/stats";

        mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(statsJson(120, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highScore", is(120)))
                .andExpect(jsonPath("$.totalMatches", is(1)));

        // Skor lebih rendah tidak menurunkan highScore, tetapi pertandingan tetap dihitung.
        mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(statsJson(40, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highScore", is(120)))
                .andExpect(jsonPath("$.totalMatches", is(2)));

        // matchCompleted=false memperbarui skor tanpa menambah jumlah pertandingan.
        mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(statsJson(200, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highScore", is(200)))
                .andExpect(jsonPath("$.totalMatches", is(2)))
                .andExpect(jsonPath("$.username", is("StatsUser")))
                .andExpect(jsonPath("$.snakeType", is("COBRA")));
    }

    @Test
    @DisplayName("PATCH /api/v1/players/{id}/stats - 400 Bad Request untuk skor negatif")
    void patchStatsNegativeScoreReturnsBadRequest() throws Exception {
        String id = register("NegativeScore", "VIPER").get("id").asText();
        mockMvc.perform(patch(BASE + "/" + id + "/stats").contentType(MediaType.APPLICATION_JSON).content(statsJson(-5, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.invalidFields.score", notNullValue()));
    }

    @Test
    @DisplayName("PATCH /api/v1/players/{id}/stats - 404 Not Found untuk ID yang tidak terdaftar")
    void patchStatsUnknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(patch(BASE + "/PL-99999/stats").contentType(MediaType.APPLICATION_JSON).content(statsJson(10, true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("PL-99999")));
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    @DisplayName("DELETE /api/v1/players/{id} - 204 No Content lalu GET mengembalikan 404 dan pemain hilang dari daftar")
    void deleteRemovesPlayer() throws Exception {
        String id = register("DeleteMe", "PYTHON").get("id").asText();

        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]", empty()));
    }

    @Test
    @DisplayName("DELETE /api/v1/players/{id} - 404 Not Found ketika ID yang sama dihapus untuk kedua kalinya")
    void deleteTwiceReturnsNotFound() throws Exception {
        String id = register("DeleteTwice", "VIPER").get("id").asText();
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is(BASE + "/" + id)))
                .andExpect(jsonPath("$.message", containsString(id)));
    }

    @Test
    @DisplayName("DELETE /api/v1/players/{id} - username pemain terhapus dapat didaftarkan ulang tanpa tag")
    void deletedUsernameCanBeRegisteredAgain() throws Exception {
        String id = register("ReusableName", "COBRA").get("id").asText();
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        JsonNode again = register("ReusableName", "VIPER");
        assertEquals("ReusableName", again.get("username").asText());
        assertNotEquals(id, again.get("id").asText());
    }
}
