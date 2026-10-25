package com.riotanalizer.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiotApiClientErrorTest {

    @Test
    void extractsStatusMessageFromRiotErrorBody() {
        String body = "{\"status\":{\"message\":\"Bad Request - Exception decrypting abc\",\"status_code\":400}}";
        assertEquals("Bad Request - Exception decrypting abc", RiotApiClient.extractRiotMessage(body));
    }

    @Test
    void fallsBackToRawBodyAndNullForEmpty() {
        assertEquals("upstream exploded", RiotApiClient.extractRiotMessage("  upstream exploded \n"));
        assertNull(RiotApiClient.extractRiotMessage(""));
        assertNull(RiotApiClient.extractRiotMessage(null));
    }

    @Test
    void decryptionErrorMessageExplainsTheKeySwitch() {
        String msg = RiotApiClient.describeError(400, "Bad Request", "Bad Request - Exception decrypting abc");
        assertTrue(msg.startsWith("Riot API error: 400 Bad Request – Bad Request - Exception decrypting abc"), msg);
        assertTrue(msg.contains("different API key"), msg);
    }

    @Test
    void forbiddenMessageHintsAtTheKey() {
        String msg = RiotApiClient.describeError(403, "Forbidden", "Forbidden");
        assertEquals("Riot API error: 403 Forbidden (hint: API key is invalid/expired, or it has no access to this endpoint)", msg);
    }
}
