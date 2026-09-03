package com.riotanalizer.client;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thin wrapper over the Riot API. Every outbound call first acquires a token
 * from the shared {@link RiotRateLimiter}.
 */
@Component
public class RiotApiClient {

    private static final Logger log = LoggerFactory.getLogger(RiotApiClient.class);

    private final RestClient platform;
    private final RestClient regional;
    private final RiotRateLimiter rateLimiter;

    public RiotApiClient(@Qualifier(RestClientConfig.PLATFORM) RestClient platform,
                         @Qualifier(RestClientConfig.REGIONAL) RestClient regional,
                         RiotRateLimiter rateLimiter) {
        this.platform = platform;
        this.regional = regional;
        this.rateLimiter = rateLimiter;
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
            log.debug("Riot API ← {} failed: {} {}", desc, e.getStatusCode().value(), e.getStatusText());
            throw translate(e);
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
        return new RiotApiException(status, "Riot API error: " + status + " " + e.getStatusText());
    }
}
