package com.snake.arenaservice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class PlayerServiceClientTest {

    @Test
    @DisplayName("Test Resilience4j Fallback - Menangani kegagalan player-service secara aman tanpa crash")
    void testSyncScoreFallbackGracefulHandling() {
        PlayerServiceClient client = new PlayerServiceClient("http://localhost:8081");
        
        // Memastikan fallback method tidak melempar exception saat player-service unreachable
        assertDoesNotThrow(() -> client.syncScoreFallback(
                "PL-TEST-999",
                350,
                true,
                new RuntimeException("Connection refused / player-service down")
        ));
    }
}
