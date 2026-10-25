package com.riotanalizer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.client.dto.LeagueEntryDto;
import com.riotanalizer.client.dto.SummonerDto;
import com.riotanalizer.config.RestClientConfig;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RateLimitedException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.ratelimit.RiotRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thin wrapper over the Riot API. Every outbound call first acquires a token
 * from the shared {@link RiotRateLimiter}, at the priority of the calling
 * thread's {@link com.riotanalizer.ratelimit.FetchContext} (REFRESH if unset).
 */
@Component
public class RiotApiClient {

    private static final Logger log = LoggerFactory.getLogger(RiotApiClient.class);
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();
    private static final int MAX_RAW_BODY_IN_MESSAGE = 300;

    private final RestClient platform;
    private final RestClient regional;
    private final RiotRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;

    public RiotApiClient(@Qualifier(RestClientConfig.PLATFORM) RestClient platform,
                         @Qualifier(RestClientConfig.REGIONAL) RestClient regional,
                         RiotRateLimiter rateLimiter,
                         ApplicationEventPublisher events) {
        this.platform = platform;
        this.regional = regional;
        this.rateLimiter = rateLimiter;
        this.events = events;
    }

    // ---- ACCOUNT-V1 (regional) ----

    public AccountDto getAccountByRiotId(String gameName, String tagLine) {
        return call("account by-riot-id " + gameName + "#" + tagLine, () -> regional.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(AccountDto.class));
    }

    // ---- SUMMONER-V4 (platform) ----

    public SummonerDto getSummonerByPuuid(String puuid) {
        return call("summoner by-puuid", () -> platform.get()
                .uri("/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(SummonerDto.class));
    }

    // ---- LEAGUE-V4 (platform) ----

    public List<LeagueEntryDto> getLeagueEntriesByPuuid(String puuid) {
        List<LeagueEntryDto> entries = call("league entries by-puuid", () -> platform.get()
                .uri("/lol/league/v4/entries/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LeagueEntryDto>>() {}));
        return entries == null ? List.of() : entries;
    }

    // ---- MATCH-V5 (regional) ----

    public List<String> getMatchIds(String puuid, int start, int count) {
        return getMatchIds(puuid, start, count, null);
    }

    /**
     * Match ids for a player. When {@code startTimeSec} is non-null it is passed
     * as the Match-V5 {@code startTime} (epoch seconds) filter, so only matches
     * played on/after that instant are returned — used to page through the
     * current season. {@code start}/{@code count} drive pagination (count max 100).
     */
    public List<String> getMatchIds(String puuid, int start, int count, Long startTimeSec) {
        List<String> ids = call("match ids start=" + start + " count=" + count, () -> regional.get()
                .uri(uri -> {
                    uri.path("/lol/match/v5/matches/by-puuid/{puuid}/ids")
                            .queryParam("start", start)
                            .queryParam("count", count);
                    if (startTimeSec != null) {
                        uri.queryParam("startTime", startTimeSec);
                    }
                    return uri.build(puuid);
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {}));
        return ids == null ? List.of() : ids;
    }

    public JsonNode getMatch(String matchId) {
        return call("match " + matchId, () -> regional.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode getMatchTimeline(String matchId) {
        return call("match timeline " + matchId, () -> regional.get()
                .uri("/lol/match/v5/matches/{matchId}/timeline", matchId)
                .retrieve()
                .body(JsonNode.class));
    }

    // ---- SPECTATOR-V5 (platform) ---- 404 = not in a game

    public Optional<JsonNode> getActiveGameByPuuid(String puuid) {
        log.debug("Riot API → spectator active-game by-puuid");
        rateLimiter.acquire();
        try {
            JsonNode node = platform.get()
                    .uri("/lol/spectator/v5/active-games/by-summoner/{puuid}", puuid)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(node);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Riot API ← spectator: player not in an active game");
                return Optional.empty();
            }
            throw translate(e);
        }
    }

    // ---- helpers ----

    private <T> T call(String desc, Supplier<T> supplier) {
        log.debug("Riot API → {}", desc);
        rateLimiter.acquire();
        try {
            return supplier.get();
        } catch (RestClientResponseException e) {
            RuntimeException translated = translate(e);
            log.debug("Riot API ← {} failed: {}", desc, translated.getMessage());
            throw translated;
        }
    }

    private RuntimeException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 404) {
            return new NotFoundException("Riot resource not found");
        }
        if (status == 429) {
            return new RateLimitedException("Riot API rate limit exceeded");
        }
        String riotMessage = extractRiotMessage(e.getResponseBodyAsString());
        RiotApiException ex = new RiotApiException(status, describeError(status, e.getStatusText(), riotMessage), riotMessage);
        if (ex.isDecryptionError()) {
            events.publishEvent(new PuuidKeyMismatchEvent(riotMessage));
        }
        return ex;
    }

    /**
     * Human-readable error, including Riot's own explanation and a hint for the
     * common causes, e.g.
     * {@code Riot API error: 400 Bad Request – Exception decrypting ... (hint: ...)}.
     */
    static String describeError(int status, String statusText, String riotMessage) {
        StringBuilder msg = new StringBuilder("Riot API error: ").append(status);
        if (statusText != null && !statusText.isBlank()) {
            msg.append(' ').append(statusText.trim());
        }
        if (riotMessage != null && !riotMessage.isBlank()
                && !riotMessage.trim().equalsIgnoreCase(String.valueOf(statusText).trim())) {
            msg.append(" – ").append(riotMessage.trim());
        }
        if (RiotApiException.isDecryptionError(status, riotMessage)) {
            msg.append(" (hint: this PUUID/summonerId was issued for a different API key's application,"
                    + " e.g. stored with a development key and now used with a personal/production key;"
                    + " it must be re-resolved by Riot ID with the current key)");
        } else if (status == 401) {
            msg.append(" (hint: no API key sent – check RIOT_API_KEY)");
        } else if (status == 403) {
            msg.append(" (hint: API key is invalid/expired, or it has no access to this endpoint)");
        }
        return msg.toString();
    }

    /**
     * Riot error bodies look like {@code {"status":{"message":"...","status_code":400}}}.
     * Returns {@code status.message}, a truncated raw body if it isn't that
     * shape, or {@code null} for an empty body.
     */
    static String extractRiotMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = ERROR_MAPPER.readTree(body);
            JsonNode message = node.path("status").path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
            JsonNode top = node.path("message");
            if (top.isTextual() && !top.asText().isBlank()) {
                return top.asText();
            }
        } catch (Exception ignored) {
            // not JSON – fall through to the raw body
        }
        String raw = body.strip();
        return raw.length() > MAX_RAW_BODY_IN_MESSAGE ? raw.substring(0, MAX_RAW_BODY_IN_MESSAGE) + "…" : raw;
    }
}
