# Riot Analizer — Shared API Contract & Architecture

This is the single source of truth both the **backend** (Spring Boot) and **frontend** (React) build against. Do not deviate from these shapes.

## Overview

A LoL stats app for a single tracked player (extensible to many). Data is pulled from the Riot API by a **rate-limited scheduled fetcher**, stored in PostgreSQL, and served to a React SPA. **Only current-season matches** are considered for stats.

- Region: **EUNE** → platform host `eun1.api.riotgames.com`, regional (account/match) host `europe.api.riotgames.com`.
- Riot ID format everywhere: `gameName` + `tagLine` (e.g. `Faker` / `KR1`).
- Backend base path: `/api`. Backend listens on container port **8080** (host port is randomized by Docker).
- CORS: allow the frontend origin (dev: `http://localhost:5173`; prod served same-origin via nginx proxy — see infra).

## Current-season handling

Riot's match-v5 no longer returns a season id. "Current season" = matches whose `gameCreation` (epoch ms) >= `riot.current-season-start-epoch-ms` (config, env `RIOT_CURRENT_SEASON_START_EPOCH_MS`, default = `1736380800000` i.e. 2025-01-09, override per real season). All stats queries filter on this.

## Rate limiting (dev key)

Dev key limits: **20 requests / 1 s** and **100 requests / 120 s**, applied app-wide across ALL Riot calls. Implement a shared limiter (two token buckets, acquire from both before every outbound Riot request). The scheduled fetcher must never burst past this; it blocks/awaits tokens.

## Scheduler

`@Scheduled(fixedDelayString = "${riot.fetch-interval-ms:600000}")` (default 10 min). For each tracked player: refresh account/summoner/rank, fetch newest match ids since last stored match, fetch+persist any missing match details (all 10 participants stored). Also refresh the live-game cache. All calls go through the rate limiter.

## Persistence (store ALL 10 participants per match)

Storing every participant is what makes vs-player and vs-champion possible.

- `player`(puuid PK, game_name, tag_line, summoner_id, profile_icon_id, summoner_level, solo_* rank fields, flex_* rank fields, last_fetched, tracked boolean)
- `match`(match_id PK, queue_id, game_creation bigint, game_duration int, game_version, map_id)
- `participant`(id PK, match_id FK, puuid, riot_id_game_name, riot_id_tag_line, champion_id, champion_name, team_id, team_position, win, kills, deaths, assists, total_cs (minions+neutral), vision_score, total_damage_to_champions, gold_earned, item0..item6, summoner1_id, summoner2_id)

## REST Endpoints (all under `/api`)

All player-addressing endpoints take `{gameName}/{tagLine}` path segments (URL-encoded).

1. `GET /api/players/{gameName}/{tagLine}` → `PlayerDto`. Resolves via Riot if unknown, registers as tracked, returns profile + ranks.
2. `GET /api/players/{gameName}/{tagLine}/stats?queue={queueId?}` → `PlayerStatsDto`. Current-season aggregate. Optional queue filter (e.g. 420 solo, 440 flex, 400 normal draft). Omit = all queues.
3. `GET /api/players/{gameName}/{tagLine}/matches?page={0}&size={20}&queue={queueId?}` → `Page<MatchSummaryDto>` (Spring Page JSON: `content`, `totalElements`, `totalPages`, `number`, `size`).
4. `GET /api/players/{gameName}/{tagLine}/live` → `LiveMatchDto` (`{ "inGame": false }` when not in a game; HTTP 200 either way).
5. `GET /api/players/{gameName}/{tagLine}/vs/{oppGameName}/{oppTagLine}` → `HeadToHeadDto`. Matches (current season) where both puuids appear.
6. `GET /api/players/{gameName}/{tagLine}/vs-champion/{championName}` → `VsChampionDto`. Current-season matches where an ENEMY participant played `championName`; the tracked player's record & KDA in those games.
7. `POST /api/players/{gameName}/{tagLine}/refresh` → triggers an immediate (rate-limited) fetch for that player. Returns `PlayerDto`. 202/200.
8. `GET /api/tracked` → `PlayerDto[]` list of tracked players.
9. `GET /api/health` → `{ "status": "UP" }` (in addition to actuator).
10. `GET /api/players/{gameName}/{tagLine}/opponents?minGames={2}&limit={200}` → `OpponentSummaryDto[]` — the "vs player" DASHBOARD: every opponent faced on the enemy team this season (default only those met >= minGames times), most-faced first. Drill into one via endpoint 5.
11. `GET /api/players/{gameName}/{tagLine}/champion-matchups?limit={300}` → `ChampionMatchupSummaryDto[]` — the "vs champion" DASHBOARD: every enemy champion faced this season, most-faced first. Drill into one via endpoint 6.
12. `GET /api/players/{gameName}/{tagLine}/teammates?minGames={2}&limit={200}` → `TeammateSummaryDto[]` — the "with player" (duo) DASHBOARD: every teammate played alongside on the SAME team this season (default only those met >= minGames times), most-played first. Drill into one via endpoint 5 (head-to-head reports both same-team and opposing games).
13. `GET /api/players/{gameName}/{tagLine}/lane-opponents?limit={200}` → `LaneOpponentSummaryDto[]` — every enemy champion faced in the tracked player's OWN position (their actual lane opponent) this season, most-faced first. Drill into one via endpoint 6.
14. `GET /api/players/{gameName}/{tagLine}/roles?queue={queueId?}` → `RolesDto` — per-position (lane/role) performance this season. Optional queue filter.
15. `GET /api/players/{gameName}/{tagLine}/time?queue={queueId?}&tz={offsetMinutes}` → `TimeStatsDto` — win rate by local hour-of-day, weekday, and position within a play session (tilt). `tz` is the browser `Date.getTimezoneOffset()` (minutes; default 0 = UTC).
16. `GET /api/players/{gameName}/{tagLine}/insights?queue={queueId?}&tz={offsetMinutes}` → `InsightsDto` — auto-generated plain-language findings (streaks, nemesis, best duo, bogey champion, strongest role, tilt, best day) plus streak counters. Each highlight carries a drill-down link (`linkKind`/`linkValue`).

```
OpponentSummaryDto { puuid, gameName, tagLine, games, wins, losses, winRate }   // games=opposing games; wins/losses/winRate from tracked player's POV; winRate 0..100
ChampionMatchupSummaryDto { championId, championName, games, wins, losses, winRate, avgKills, avgDeaths, avgAssists, avgKda }
TeammateSummaryDto { puuid, gameName, tagLine, games, wins, losses, winRate }   // games=shared same-team games; win/loss shared; winRate 0..100
LaneOpponentSummaryDto { championId, championName, games, wins, losses, winRate, avgKills, avgDeaths, avgAssists, avgKda }  // enemy champ in your own position
```
Note: opponent `gameName`/`tagLine` may be null for older matches where Riot didn't return them (group is still keyed by puuid).

Errors: JSON `{ "error": string, "status": int, "path": string, "timestamp": string }`. 404 when Riot returns not found; 429 surfaced as 503 with retry note.

## DTO shapes (JSON)

```
PlayerDto {
  puuid: string, gameName: string, tagLine: string,
  summonerLevel: number, profileIconId: number,
  soloRank: RankDto | null, flexRank: RankDto | null,
  tracked: boolean, lastFetched: string|null (ISO)
}
RankDto { queueType: string, tier: string, rank: string, leaguePoints: number, wins: number, losses: number, winRate: number }

PlayerStatsDto {
  gameName, tagLine,
  gamesPlayed, wins, losses, winRate,        // winRate 0..100
  avgKills, avgDeaths, avgAssists, avgKda,
  avgCsPerMin, avgVisionScore, avgDamageToChampions, avgGoldEarned,
  avgGameDurationSec,
  recentForm: boolean[],                      // most-recent-first, true=win, up to 20
  championStats: ChampionStatDto[]            // sorted by games desc
}
ChampionStatDto { championId, championName, games, wins, losses, winRate, avgKills, avgDeaths, avgAssists, avgKda, avgCsPerMin }

MatchSummaryDto {
  matchId, queueId, queueName, gameCreation (epoch ms), gameDurationSec,
  championId, championName, win,
  kills, deaths, assists, kda, cs, csPerMin, visionScore, damageToChampions, goldEarned,
  teamPosition, items: number[7], summonerSpell1: number, summonerSpell2: number
}

LiveMatchDto {
  inGame: boolean,
  gameId?: number, queueId?, gameMode?, gameStartTime? (epoch ms), gameLengthSec?, mapId?,
  participants?: LiveParticipantDto[]
}
LiveParticipantDto { puuid, riotId (gameName#tagLine or null), championId, championName, teamId, spell1Id, spell2Id, tier?, rank?, leaguePoints? }

HeadToHeadDto {
  player: {gameName, tagLine}, opponent: {gameName, tagLine},
  totalSharedGames, sameTeamGames, opposingGames,
  playerWinsWhenOpposing, opponentWinsWhenOpposing,
  matches: H2HMatchDto[]
}
H2HMatchDto { matchId, gameCreation, sameTeam: boolean, playerChampionName, opponentChampionName, playerWin, playerKills, playerDeaths, playerAssists, opponentKills, opponentDeaths, opponentAssists, queueName }

VsChampionDto {
  gameName, tagLine, championName,
  gamesAgainst, wins, losses, winRate, avgKills, avgDeaths, avgAssists, avgKda,
  matches: VsChampionMatchDto[]
}
VsChampionMatchDto { matchId, gameCreation, playerChampionName, win, kills, deaths, assists, queueName }

RolesDto { gameName, tagLine, totalGames, roles: RoleStatDto[] }               // roles sorted by games desc
RoleStatDto { position, games, wins, losses, winRate, avgKills, avgDeaths, avgAssists, avgKda, avgCsPerMin, avgVisionScore }  // position: TOP|JUNGLE|MIDDLE|BOTTOM|UTILITY|NONE

TimeStatsDto { gameName, tagLine, totalGames, byHour: TimeBucketDto[24], byWeekday: TimeBucketDto[7], bySession: TimeBucketDto[] }
TimeBucketDto { key, label, games, wins, winRate }                             // key: hour 0-23 | weekday 1-7 (Mon-Sun) | session position; winRate 0..100

InsightsDto { gameName, tagLine, gamesAnalyzed, currentStreak, longestWinStreak, longestLossStreak, highlights: InsightDto[] }  // currentStreak signed (+win/-loss)
InsightDto { kind, icon, title, detail, sentiment, linkKind, linkValue }       // sentiment: good|bad|neutral; linkKind: vs-player|with-player|vs-champion|trends|null
```

## Champion assets (frontend)

Frontend renders champion/item icons from Data Dragon using `championName` and item ids, e.g.
`https://ddragon.leagueoflegends.com/cdn/{VER}/img/champion/{ChampionName}.png`. Frontend resolves latest `{VER}` from `https://ddragon.leagueoflegends.com/api/versions.json` at load.

## Queue names

420 Ranked Solo/Duo, 440 Ranked Flex, 400 Normal Draft, 430 Normal Blind, 450 ARAM, 700 Clash, 490 Quickplay. Unknown → "Queue {id}".

---

## Addendum: Performance over time (added)

Powers the "Performance over time" panel on the **Trends** page — a per-game metric trend with a rolling-average trendline.

### Endpoint

17. `GET /api/players/{gameName}/{tagLine}/performance?queue={queueId?}` → `PerformanceTrendDto`. The tracked player's current-season games as a per-game metric series, **oldest game first** (so it charts left-to-right). Optional queue filter, same semantics as the other stats endpoints. No new persistence — computed from stored participations.

Each point keeps its `teamPosition`; the client offers a **role filter** so a metric is charted for one position at a time (mixing e.g. support and mid CS/min in one line would be misleading). Metric selection (KDA, CS/min, Dmg/min, Vision, Gold/min), rolling-average smoothing and the linear net-change caption are all computed client-side from these points. Points are clickable through to `/matches/{matchId}`.

```
PerformanceTrendDto { gameName, tagLine, totalGames, points: PerfPointDto[] }   // points oldest-first
PerfPointDto {
  gameCreation,                 // epoch ms
  matchId, championId, championName, teamPosition,
  win, kills, deaths, assists, kda,
  cs, csPerMin, visionScore, damageToChampions, goldEarned,
  gameDurationSec, queueId
}
```

### Trends-tab filters (added)

Endpoints **14 `/roles`** and **15 `/time`** now accept optional `champion` and `role` query params (in addition to `queue`), so the whole Trends tab can be filtered by a single champion (the Match-V5 `championName`, e.g. `Velkoz`) and/or a single position (`TOP|JUNGLE|MIDDLE|BOTTOM|UTILITY`). Both filter the season participations before aggregating; omitting them preserves the previous behaviour. The Insights endpoint (16) is unaffected — it calls the unfiltered overloads. The performance endpoint (17) is filtered client-side from its per-game points.
