# Riot Analizer — New Analytics Features Spec

Implementation-ready design for six new stats/insights, written against the existing
codebase conventions (JPQL aggregation rows in `ParticipantRepository`, the distilled
`match_timeline_cache`, `StatMath`/`TrendMath` helpers, the `PlayerController` +
`api.ts` + `types.ts` + Trends/recharts frontend patterns).

Every feature is current-season-scoped and queue-filterable, consistent with the
existing endpoints. All new numeric helpers go in `StatMath`/`TrendMath` and get unit
tests alongside `StatMathTest`/`TrendMathTest`/`StatsServiceTest`.

---

## 0. Shared foundation — Timeline backfill + derived extraction

Three of the six features (Laning, Death heatmap, Comeback/throw) need timeline-derived
data for **every** season game. Today `MatchTimelineService.analysis()` fetches and caches
a timeline **only when a user opens a match's analysis screen**, so most games have no
cached timeline. This foundation must exist before features 1, 3 and 4.

The good news: the distilled timeline JSON already stored in `match_timeline_cache`
contains everything those three features need — per-frame `totalGold`/`xp`/`cs`/`level`/`x`/`y`
per participant, and `CHAMPION_KILL` events with `x`/`y`/`killerId`/`victimId`. So the
foundation is two pieces:

**(a) Backfill component** — `TimelineBackfillService`
- Iterate stored matches that lack a fresh cached timeline (or lack derived rows for the
  current derived-schema version) and call `MatchTimelineService.analysis(matchId)`, which
  already fetches + caches through the shared `RiotRateLimiter`.
- Trigger points:
  - In `ScheduledFetcher.run()`, after `fetchService.fetchAndStore(player)`, enqueue the
    newly stored match ids for backfill (they're the delta — cheap).
  - A periodic sweep (`@Scheduled`, long fixed delay, e.g. hourly) that picks up a bounded
    batch of historical matches still missing derived rows, so a fresh DB fills in over time
    without bursting the dev-key limit.
- Rate-limit note: dev key is 20 req/s and 100 req/120s; a timeline is **one** call per
  match. The existing limiter blocks as needed, so backfill is safe but a full historical
  season (hundreds of games) fills gradually. Make batch size configurable
  (`riot.backfill-batch-size`, default e.g. 25 per sweep).

**(b) Derivation pass** — right after `analysis()` caches a timeline, parse it once and
write three small, aggregate-friendly derived tables (all keyed by `match_id` + `puuid`
of the tracked player, populated only for tracked players' participations):

| Table | Columns | Feeds |
|---|---|---|
| `early_game_stat` | matchId, puuid, position, goldAt10, csAt10, xpAt10, goldDiff10, csDiff10, xpDiff10, goldAt14, csDiff14, firstBloodParticipant(bool) | Feature 1 |
| `player_death` | id, matchId, puuid, x, y, timestampMs, minute, killerChampion, position | Feature 3 |
| `match_gold_state` | matchId, puuid, teamId, leadAt10, leadAt14, leadAt15, leadAt20, win | Feature 4 |

- `goldDiff*`/`csDiff*` are player-minus-lane-opponent (lane opponent = enemy participant
  with the same `teamPosition`; null-safe when there's no positional opponent, e.g. ARAM).
- `lead*` = (sum of my team's `totalGold` at the nearest frame to that minute) − (sum of enemy
  team's `totalGold`), from the distilled frames.
- Add a `DERIVED_SCHEMA_VERSION` constant mirroring the existing `SCHEMA_VERSION` pattern so
  re-extraction can be forced when the shape changes.

**Why derived tables rather than re-parsing JSON per request:** it keeps the new endpoints
as simple SQL `group by` aggregations — identical in spirit to the existing
`aggregateOpponents` / `aggregateChampionMatchups` queries — instead of deserializing many
large JSON blobs on every page load.

**Effort:** Medium-High. This is the single biggest cost item and the prerequisite for
features 1, 3, 4. Build it once, right after the three cheap features.

---

## Feature 5 — Carry Index  *(build first: cheapest, no dependencies)*

**What it answers:** "Am I the one carrying my team, or getting carried?" — and does carrying
correlate with winning?

**Data:** Uses only the already-stored 10 participants per match. **No timeline, no backfill.**

**Backend**
- New aggregation row + query in `ParticipantRepository`. One grouped query gives, per game,
  the player's numbers and the team totals/max:

```java
@Query("""
    select new com.riotanalizer.dto.CarryRow(
        m.matchId, me.win,
        me.totalDamageToChampions, me.goldEarned, me.kills, me.assists,
        sum(t.totalDamageToChampions), sum(t.goldEarned), sum(t.kills), sum(t.assists),
        max(t.totalDamageToChampions))
    from Participant me
    join me.match m
    join m.participants t
    where me.puuid = :puuid
      and m.gameCreation >= :seasonStart
      and t.teamId = me.teamId
      and (:queueId is null or m.queueId = :queueId)
    group by m.matchId, me.win, me.totalDamageToChampions, me.goldEarned, me.kills, me.assists
    """)
List<CarryRow> aggregateCarry(@Param("puuid") String puuid,
                              @Param("seasonStart") long seasonStart,
                              @Param("queueId") Integer queueId);
```

- `CarryService` computes per game:
  - `damageShare = myDamage / teamDamage`, `goldShare = myGold / teamGold`
  - `killParticipation = (myKills + myAssists) / teamKills`
  - `isTopDamage = (myDamage == maxTeamDamage)`
  - `carryScore` per game = weighted blend, e.g. `0.5*damageShare + 0.25*goldShare + 0.25*(KP)`
    normalized so 0.2 (an even 1/5 split) maps to ~50 and 0.4+ maps toward 100.
- Season aggregates: `avgDamageShare`, `avgGoldShare`, `avgKillParticipation`,
  `topDamageRate` (% of games you're your team's top damage), `winRateWhenTopDamage` vs
  `winRateOtherwise`, and a single 0–100 `carryIndex`.
- `CarryIndexDto(gameName, tagLine, games, avgDamageShare, avgGoldShare, avgKillParticipation,
  topDamageRate, winRateWhenTopDamage, winRateOtherwise, carryIndex, List<CarryPointDto> perGame)`
  — `perGame` (matchId, gameCreation, champion, carryScore, win) powers an optional scatter.

**Endpoint:** `GET /api/players/{gameName}/{tagLine}/carry?queue=` → `CarryIndexDto`.

**Frontend**
- `api.ts`: `getCarry(gameName, tagLine, queue?)`; `types.ts`: `CarryIndexDto`, `CarryPoint`.
- New section on **Trends** (or an Overview tile): a `WinRateRing`-style ring for the 0–100
  Carry Index, three `StatTile`s (dmg share, gold share, KP), and a two-bar compare of
  "win rate when you're top damage" vs "otherwise". Optional recharts scatter of per-game
  carry score colored by win/loss.
- Optional: feed an Insight ("You carry 62% of your games and win 71% of those").

**Effort:** Low–Medium. **Depends on:** nothing.

---

## Feature 2 — Win rate by game length  *(build second: cheapest, no dependencies)*

**What it answers:** Do you stomp early and fade, or grind out long games?

**Data:** `gameDuration` + `win` on every match. **No timeline, no backfill, no new query** —
reuse `findSeasonParticipations` and bucket in the service (same pattern as `RolesService`).

**Backend**
- `GameLengthService.gameLength(player, queueId)` iterates the season participations, buckets
  by `m.getGameDuration()`:
  - `<20:00` (early stomp), `20–25`, `25–30`, `30–35`, `35:00+` (configurable edges).
  - Per bucket: games, wins, `StatMath.winRate`.
  - Also `avgWinDurationSec` vs `avgLossDurationSec` (are your wins faster than your losses?).
- `GameLengthStatsDto(gameName, tagLine, total, List<DurationBucketDto> buckets,
  double avgWinDurationSec, double avgLossDurationSec)` where
  `DurationBucketDto(key, label, minSec, maxSec, games, wins, winRate)`.

**Endpoint:** `GET /api/players/{gameName}/{tagLine}/game-length?queue=` → `GameLengthStatsDto`.

**Frontend**
- `api.ts`: `getGameLength(...)`; `types.ts`: `GameLengthStatsDto`, `DurationBucket`.
- Trends section: reuse the existing `TimeChart`/`BarChart` win-rate-by-bucket pattern
  (bars colored by `barColor(winRate, games)`), plus two `StatTile`s for avg win vs loss
  length (mm:ss via `util`).

**Effort:** Low. **Depends on:** nothing.

---

## Feature 6 — "Should I queue again?" tilt-o-meter  *(build third: logic-only, no new data)*

**What it answers:** Right now, given your session and the time, should you queue again? —
a prospective GO / CAUTION / STOP signal with a predicted next-game win probability.

**Data:** Reuses `TimeStatsService` (session-position, hour, weekday win rates), current streak
(`TrendMath.currentStreak`), and a recent-KDA slope from the last few `PerformanceService`
points. **No new stored data, no backfill.**

**Backend** — new `QueueAdviceService` depending on `ParticipantRepository`, `TimeStatsService`,
`RiotProperties`.
- Determine "now" in the player's local time from the `tz` offset (same convention as the
  `/time` and `/insights` endpoints).
- **Session position of the *next* game:** from the most recent game's end
  (`gameCreation + gameDuration`); if `now − lastEnd <= SESSION_GAP_MS` the next game is
  position `lastPos + 1`, else it's a fresh session (position 1). Reuse
  `TimeStatsService.SESSION_GAP_MS` and `TrendMath.sessionPositions`.
- **Predicted win rate:** blend three historical buckets — session-position(next),
  hour-of-day(now), weekday(now) — each **shrunk toward the player's overall season win rate**
  by sample size (e.g. `wr_adj = (w + k*overall/100) / (g + k)` with `k≈5`) so thin buckets
  don't dominate. Weighted average (session position weighted highest — it's the tilt signal).
- **Modifiers / reasons:** losing streak ≥2 → nudge down + "you're on a 3-game skid";
  deep session (next position ≥4) with a historical late-session drop → down + "your win rate
  falls after game 3"; strong hour/day → up. Recent KDA slope (last 3–5 games trending down)
  → caution flag.
- **Signal thresholds:** predicted ≥ 52% and no red flags → GO; 45–52% or one flag → CAUTION;
  < 45% or losing streak + late session → STOP.
- `QueueAdviceDto(gameName, tagLine, signal, predictedWinRate, sessionPositionNext,
  minutesSinceLastGame, int sampleSize, List<ReasonDto> reasons)` where
  `ReasonDto(text, tone /* good|bad|neutral */)`.

**Endpoint:** `GET /api/players/{gameName}/{tagLine}/queue-advice?tz=&queue=` → `QueueAdviceDto`.

**Frontend**
- `api.ts`: `getQueueAdvice(gameName, tagLine, tz, queue?)`; `types.ts`: `QueueAdviceDto`, `Reason`.
- A prominent traffic-light card (green/amber/red) on **Overview** and/or the **Live** page:
  big signal + predicted % ring + a short reasons list (tone-colored, like the Insights cards).

**Effort:** Medium (scoring logic + shrinkage tuning), but no data/infra dependency.
Unit-test the blend/shrinkage and session-position-of-next logic in a `QueueAdviceMathTest`.

---

## Feature 1 — Aggregated laning / early-game metrics  *(needs foundation §0)*

**What it answers:** "How's my laning phase?" as a season trend, not a one-off per game.

**Data:** `early_game_stat` derived rows from §0 (gold/CS/XP at 10 & 14, diffs vs lane opponent,
first-blood participation).

**Backend**
- `EarlyGameRepository` aggregation grouped by `position` (mirrors `aggregateLaneOpponents`
  style): per role → games, `avgGoldDiff10`, `avgCsDiff10`, `avgXpDiff10`, `avgGoldDiff14`,
  `avgCsDiff14`, `firstBloodRate`, plus an `overall` roll-up.
- `EarlyGameService.earlyGame(player, queueId)` → `EarlyGameStatsDto(gameName, tagLine, total,
  List<EarlyRoleRow> byRole, EarlyRoleRow overall)` where each row carries the averages above.

**Endpoint:** `GET /api/players/{gameName}/{tagLine}/early-game?queue=` → `EarlyGameStatsDto`.

**Frontend**
- Trends section "Laning": a diverging bar chart of avg gold diff @10 per role (positive =
  green/ahead, negative = red/behind — reuse the `ReferenceLine` at 0 already imported in
  Trends), `StatTile`s for CSD@10 / GD@10 / first-blood rate, and a 10-vs-14 comparison so you
  can see whether leads grow or evaporate.

**Effort:** Medium (on top of §0). **Depends on:** §0 foundation.

---

## Feature 4 — Comeback vs throw rating  *(needs foundation §0)*

**What it answers:** How often do you win from behind (resilience) vs lose from ahead (throwing)?

**Data:** `match_gold_state` derived rows from §0 (team gold lead at 10/14/15/20 + win).

**Backend**
- `MentalityService.mentality(player, queueId, checkpointMin, threshold)`:
  - Pick the checkpoint column (default `leadAt15`; allow 10/14/15/20).
  - Classify each game: `ahead` if lead ≥ +threshold (default 1500), `behind` if ≤ −threshold,
    else `even`.
  - `comebackRate` = wins among `behind` / behind games; `throwRate` = losses among `ahead` /
    ahead games; `closeRate` = wins among `ahead` (leads converted).
- `MentalityDto(gameName, tagLine, games, checkpointMin, threshold, Bucket ahead, Bucket even,
  Bucket behind, comebackRate, throwRate, closeRate)` where `Bucket(games, wins, losses, winRate)`.

**Endpoint:** `GET /api/players/{gameName}/{tagLine}/mentality?queue=&checkpointMin=15&threshold=1500`
→ `MentalityDto`.

**Frontend**
- Trends section "Mentality": two headline numbers (Comeback % green, Throw % red), a 3-row
  record table (ahead / even / behind → W-L, win rate), and controls for checkpoint + threshold.
- Optional Insight: "You throw 34% of games you're ahead at 15 min."

**Effort:** Medium (on top of §0). **Depends on:** §0 foundation (shares the derivation pass
with feature 1 — extract both in one JSON parse).

---

## Feature 3 — Season-wide death heatmap & death-timing profile  *(needs foundation §0)*

**What it answers:** Where, when, and to whom do you keep dying across the whole season?

**Data:** `player_death` derived rows from §0 (x, y, minute, killer champion, position).

**Backend**
- `DeathAnalysisService.deaths(player, queueId)` aggregates the player's `player_death` rows:
  - `points`: `[{x, y, minute}]` — cap/return binned grid cells with counts for the heatmap
    (e.g. a 32×32 grid over the ~0..15000 map coordinate space) to keep payloads small.
  - `byMinute`: deaths per game-minute bucket (0–5, 5–10, 10–15, 15–20, 20+).
  - `byKillerChampion`: champions that kill you most (top N).
  - `totalDeaths`, `games`, `avgDeathsPerGame`, optional `byPosition`.
- `DeathAnalysisDto(gameName, tagLine, totalDeaths, games, avgDeathsPerGame,
  List<HeatCell> cells, List<MinuteBucket> byMinute, List<KillerRow> byKillerChampion)`.

**Endpoint:** `GET /api/players/{gameName}/{tagLine}/deaths?queue=` → `DeathAnalysisDto`.

**Frontend**
- New sub-page or Trends section "Deaths":
  - Rift heatmap: **reuse the existing `riftmap` rendering + `toXY` coordinate transform and
    `map11.png`** from `MatchAnalysis.tsx` — render binned cells as opacity-scaled dots/squares.
  - A by-minute bar chart ("you die most at 10–15 min").
  - A top-killers list (champion icon via `ddragon` + death count).

**Effort:** Medium–High (most new UI). **Depends on:** §0 foundation.

---

## Recommended build order

Three quick wins first (immediate value, zero infra), then the shared foundation, then the
three timeline features on top of it:

1. **Feature 5 — Carry Index** (no deps; uses stored participants only)
2. **Feature 2 — Win rate by game length** (no deps; pure aggregation)
3. **Feature 6 — Tilt-o-meter** (no deps; logic over existing services)
4. **§0 — Timeline backfill + derivation pass** (foundation for the rest)
5. **Feature 1 — Aggregated laning metrics** (on §0)
6. **Feature 4 — Comeback vs throw** (on §0; shares the derivation pass with #1)
7. **Feature 3 — Death heatmap** (on §0; heaviest UI)

## Cross-cutting conventions to keep

- New math → `StatMath`/`TrendMath`; unit-test in the matching `*MathTest` / `*ServiceTest`.
- All endpoints: current-season scoped via `RiotProperties.getCurrentSeasonStartEpochMs()`,
  optional `queue` param, `tz` where local time matters — matching the existing controller.
- Aggregations follow the existing `new com.riotanalizer.dto.XxxRow(...)` JPQL projection style.
- Frontend: add typed `getXxx` to `api.ts`, interfaces to `types.ts`, render with the existing
  `ui.tsx` primitives (`Panel`, `StatTile`, `WinRateRing`, `WinRateBar`, `Segmented`) and
  recharts, mostly as new sections on the **Trends** page (plus Overview/Live cards for #5/#6).
