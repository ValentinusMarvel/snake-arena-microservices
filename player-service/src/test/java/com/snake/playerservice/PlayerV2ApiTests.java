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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pengujian endpoint GET, POST, PUT, PATCH, dan DELETE pada Player Service API v2.0.
 * Setiap pengujian membuat pemainnya sendiri melalui POST v2 lalu memverifikasi efeknya melalui GET.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerV2ApiTests {

    private static final String V2 = "/api/v2/players";
    private static final String V1 = "/api/v1/players";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String playerJson(String username, String className) {
        return String.format(
                "{\"username\":\"%s\",\"classProfile\":{\"className\":\"%s\",\"passiveEffect\":\"POISON_TRAIL\","
                        + "\"activeAbility\":{\"name\":\"Venom Dash\",\"cooldownSeconds\":5,\"speedMultiplier\":1.8}}}",
                username, className);
    }

    /** Helper: kirim POST registrasi v2 dan kembalikan body respons sebagai JsonNode. */
    private JsonNode register(String username, String className) throws Exception {
        String res = mockMvc.perform(post(V2).contentType(MediaType.APPLICATION_JSON).content(playerJson(username, className)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // ID dibangkitkan dari System.currentTimeMillis(); jeda kecil mencegah dua POST beruntun jatuh di milidetik yang sama.
        Thread.sleep(2);
        return objectMapper.readTree(res);
    }

    // ------------------------------------------------------------------ POST

    @ParameterizedTest(name = "kelas {0} -> passive {1}, ability {2}, speed v1 {3}")
    @CsvSource({
            "VIPER, POISON_TRAIL, Venom Dash, 7",
            "PYTHON, THICK_BODY, Titan Forcefield, 5",
            "COBRA, GOLDEN_SENSE, Apple Magnet, 6"
    })
    @DisplayName("POST /api/v2/players - 201 Created, profil kelas kanonis dan speed tersimpan sesuai kelas")
    void postCreatesPlayerWithCanonicalProfileAndSpeed(String className, String passive, String ability, int speed) throws Exception {
        JsonNode created = register("V2Post" + className, className);
        String id = created.get("id").asText();

        mockMvc.perform(get(V2 + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classProfile.className", is(className)))
                .andExpect(jsonPath("$.classProfile.passiveEffect", is(passive)))
                .andExpect(jsonPath("$.classProfile.activeAbility.name", is(ability)))
                .andExpect(jsonPath("$.playerTier", is("Bronze")))
                .andExpect(jsonPath("$.snakeType").doesNotExist())
                .andExpect(jsonPath("$.speed").doesNotExist());

        // Pemain yang dibuat lewat v2 harus terbaca konsisten lewat kontrak v1.
        mockMvc.perform(get(V1 + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snakeType", is(className)))
                .andExpect(jsonPath("$.speed", is(speed)));
    }

    @Test
    @DisplayName("POST /api/v2/players - className huruf kecil dinormalisasi menjadi huruf besar")
    void postNormalizesClassNameCase() throws Exception {
        mockMvc.perform(post(V2).contentType(MediaType.APPLICATION_JSON).content(playerJson("V2LowerCase", "python")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classProfile.className", is("PYTHON")));
    }

    @Test
    @DisplayName("POST /api/v2/players - 400 Bad Request untuk className di luar VIPER, PYTHON, COBRA")
    void postRejectsUnknownClassName() throws Exception {
        mockMvc.perform(post(V2).contentType(MediaType.APPLICATION_JSON).content(playerJson("V2Dragon", "DRAGON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields['classProfile.className']", notNullValue()));
    }

    @ParameterizedTest(name = "username \"{0}\"")
    @CsvSource({"ab", "abcdefghijklmnopqrstuvwxyz01234"})
    @DisplayName("POST /api/v2/players - 400 Bad Request untuk username di luar batas 3 sampai 30 karakter")
    void postRejectsUsernameOutsideLengthBounds(String username) throws Exception {
        mockMvc.perform(post(V2).contentType(MediaType.APPLICATION_JSON).content(playerJson(username, "VIPER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields.username", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v2/players - 400 Bad Request untuk payload flat v1 (breaking change)")
    void postRejectsLegacyV1Payload() throws Exception {
        mockMvc.perform(post(V2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"V2Legacy\",\"snakeType\":\"VIPER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields.classProfile", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v2/players - username duplikat mendapat tag #XXXX otomatis")
    void postAppendsTagForDuplicateUsername() throws Exception {
        register("V2Twin", "COBRA");
        mockMvc.perform(post(V2).contentType(MediaType.APPLICATION_JSON).content(playerJson("V2Twin", "COBRA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", matchesPattern("V2Twin#\\d{4}")));
    }

    // ------------------------------------------------------------------- GET

    @Test
    @DisplayName("GET /api/v2/players - 200 OK memuat pemain terdaftar dalam format v2")
    void getAllReturnsV2Representation() throws Exception {
        String id = register("V2Listed", "VIPER").get("id").asText();

        mockMvc.perform(get(V2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].classProfile.className", contains("VIPER")))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].playerTier", contains("Bronze")));
    }

    @Test
    @DisplayName("GET /api/v2/players/{id} - 404 Not Found untuk ID yang tidak terdaftar")
    void getByIdReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get(V2 + "/PL-TIDAK-ADA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // ------------------------------------------------------------------- PUT

    @Test
    @DisplayName("PUT /api/v2/players/{id} - 200 OK mengganti username dan kelas, speed dihitung ulang, spasi dipangkas")
    void putReplacesProfileAndRecalculatesSpeed() throws Exception {
        String id = register("V2PutOrigin", "VIPER").get("id").asText();

        mockMvc.perform(put(V2 + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(playerJson("  V2PutRenamed  ", "PYTHON")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("V2PutRenamed")))
                .andExpect(jsonPath("$.classProfile.className", is("PYTHON")))
                .andExpect(jsonPath("$.classProfile.activeAbility.name", is("Titan Forcefield")));

        mockMvc.perform(get(V1 + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snakeType", is("PYTHON")))
                .andExpect(jsonPath("$.speed", is(5)));
    }

    @Test
    @DisplayName("PUT /api/v2/players/{id} - 400 Bad Request untuk username milik pemain lain")
    void putRejectsUsernameOwnedByAnotherPlayer() throws Exception {
        register("V2PutOwner", "VIPER");
        String id = register("V2PutOther", "COBRA").get("id").asText();

        mockMvc.perform(put(V2 + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(playerJson("V2PutOwner", "COBRA")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(V2 + "/" + id))
                .andExpect(jsonPath("$.username", is("V2PutOther")));
    }

    @Test
    @DisplayName("PUT /api/v2/players/{id} - 400 Bad Request untuk className tidak dikenal, data tidak berubah")
    void putRejectsUnknownClassName() throws Exception {
        String id = register("V2PutClass", "VIPER").get("id").asText();

        mockMvc.perform(put(V2 + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(playerJson("V2PutClass", "DRAGON")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(V2 + "/" + id))
                .andExpect(jsonPath("$.classProfile.className", is("VIPER")));
    }

    @Test
    @DisplayName("PUT /api/v2/players/{id} - 404 Not Found untuk ID yang tidak terdaftar")
    void putReturns404ForUnknownId() throws Exception {
        mockMvc.perform(put(V2 + "/PL-TIDAK-ADA").contentType(MediaType.APPLICATION_JSON)
                        .content(playerJson("V2PutGhost", "VIPER")))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- PATCH

    @ParameterizedTest(name = "skor {0} -> tier {1}")
    @CsvSource({"49, Bronze", "50, Silver", "149, Silver", "150, Gold", "299, Gold", "300, Master"})
    @DisplayName("PATCH /api/v2/players/{id}/stats - playerTier dihitung ulang pada nilai batas skor")
    void patchRecalculatesTierAtBoundaries(int score, String tier) throws Exception {
        String id = register("V2Tier" + score, "COBRA").get("id").asText();

        mockMvc.perform(patch(V2 + "/" + id + "/stats").contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"score\":%d,\"matchCompleted\":true}", score)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highScore", is(score)))
                .andExpect(jsonPath("$.totalMatches", is(1)))
                .andExpect(jsonPath("$.playerTier", is(tier)));
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    @DisplayName("DELETE /api/v2/players/{id} - 204 No Content, lalu GET dan DELETE ulang menghasilkan 404")
    void deleteRemovesPlayer() throws Exception {
        String id = register("V2DeleteMe", "PYTHON").get("id").asText();

        mockMvc.perform(delete(V2 + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(V2 + "/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete(V2 + "/" + id)).andExpect(status().isNotFound());
    }
}
