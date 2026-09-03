package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.PlayerStatsDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private Participant participant(String matchId, boolean win, int k, int d, int a, int cs, int durationSec) {
        MatchEntity m = new MatchEntity();
        m.setMatchId(matchId);
        m.setGameDuration(durationSec);
        m.setQueueId(420);
        m.setGameCreation(1_800_000_000_000L);
        Participant p = new Participant();
        p.setMatch(m);
        p.setPuuid("puuid");
        p.setChampionId(1);
        p.setChampionName("Annie");
        p.setWin(win);
        p.setKills(k);
        p.setDeaths(d);
        p.setAssists(a);
        p.setTotalCs(cs);
        return p;
    }

    @Test
    void aggregatesWinRateAndKda() {
        ParticipantRepository repo = mock(ParticipantRepository.class);
        RiotProperties props = new RiotProperties();
        StatsService service = new StatsService(repo, props);

        // 2 games: one win 6/2/8, one loss 4/6/2  -> combined KDA = (10+10)/8 = 2.5
        List<Participant> parts = List.of(
                participant("M2", true, 6, 2, 8, 200, 1200),
                participant("M1", false, 4, 6, 2, 160, 1200)
        );
        when(repo.findSeasonParticipations(any(), anyLong(), any())).thenReturn(parts);

        Player player = new Player();
        player.setPuuid("puuid");
        player.setGameName("Test");
        player.setTagLine("EUNE");

        PlayerStatsDto stats = service.playerStats(player, null);

        assertEquals(2, stats.gamesPlayed());
        assertEquals(1, stats.wins());
        assertEquals(1, stats.losses());
        assertEquals(50.0, stats.winRate(), 1e-9);
        assertEquals(2.5, stats.avgKda(), 1e-9);
        // recentForm most-recent-first: first element is the win (M2)
        assertEquals(2, stats.recentForm().size());
        assertTrue(stats.recentForm().get(0));
        // one champion aggregate
        assertEquals(1, stats.championStats().size());
        assertEquals("Annie", stats.championStats().get(0).championName());
    }
}
