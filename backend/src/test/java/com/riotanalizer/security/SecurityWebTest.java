package com.riotanalizer.security;

import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.DiscoveredPageDto;
import com.riotanalizer.dto.PlayerDto;
import com.riotanalizer.service.CarryService;
import com.riotanalizer.service.GameLengthService;
import com.riotanalizer.service.HeadToHeadService;
import com.riotanalizer.service.InsightsService;
import com.riotanalizer.service.LiveGameService;
import com.riotanalizer.service.MatchupsService;
import com.riotanalizer.service.PerformanceService;
import com.riotanalizer.service.PlayerService;
import com.riotanalizer.service.QueueAdviceService;
import com.riotanalizer.service.RolesService;
import com.riotanalizer.service.StatsService;
import com.riotanalizer.service.TimeStatsService;
import com.riotanalizer.service.VsChampionService;
import com.riotanalizer.web.PlayerController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks of the admin login + tracking lock-down through the real filter chain. */
@WebMvcTest(controllers = {AuthController.class, PlayerController.class})
@Import({SecurityConfig.class, AdminAccount.class, LoginThrottle.class})
class SecurityWebTest {

    private static final String PASSWORD = "correct horse battery staple";

    @DynamicPropertySource
    static void adminProps(DynamicPropertyRegistry r) {
        r.add("app.admin.username", () -> "admin");
        r.add("app.admin.password-hash", () -> new BCryptPasswordEncoder(4).encode(PASSWORD));
        r.add("app.admin.max-failed-logins", () -> "3");
        r.add("app.admin.lockout-minutes", () -> "15");
        r.add("app.admin.cookie-secure", () -> "true");
        r.add("server.servlet.session.cookie.name", () -> "RA_SESSION");
    }

    @Autowired MockMvc mvc;
    @Autowired LoginThrottle throttle;

    @MockBean PlayerService playerService;
    @MockBean StatsService statsService;
    @MockBean LiveGameService liveGameService;
    @MockBean HeadToHeadService headToHeadService;
    @MockBean VsChampionService vsChampionService;
    @MockBean MatchupsService matchupsService;
    @MockBean RolesService rolesService;
    @MockBean TimeStatsService timeStatsService;
    @MockBean InsightsService insightsService;
    @MockBean PerformanceService performanceService;
    @MockBean CarryService carryService;
    @MockBean GameLengthService gameLengthService;
    @MockBean QueueAdviceService queueAdviceService;

    @BeforeEach
    void setUp() {
        throttle.recordSuccess();
        Player player = mock(Player.class);
        when(playerService.getOrRegister(anyString(), anyString())).thenReturn(player);
        when(playerService.track(anyString(), anyString())).thenReturn(player);
        when(playerService.toDto(any())).thenReturn(new PlayerDto("p1", "Name", "EUNE", 30, 1, null, null,
                true, null, "TRACKED", 0, true, null, "2026-10-25T12:00:00Z"));
        when(playerService.trackedPlayers()).thenReturn(List.of());
        when(playerService.discovered(anyInt(), anyInt(), any()))
                .thenReturn(new DiscoveredPageDto(0, 0, 0, false, 0, 0, 50, List.of()));
    }

    /** Fetches a fresh XSRF-TOKEN cookie, like the SPA does on load. */
    private Cookie xsrf() throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andReturn();
        Cookie c = r.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(c, "XSRF-TOKEN cookie issued");
        return c;
    }

    private MvcResult login(String user, String password, int expectedStatus) throws Exception {
        Cookie c = xsrf();
        return mvc.perform(post("/api/auth/login")
                        .cookie(c).header("X-XSRF-TOKEN", c.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    @Test
    void anonymousCannotSeeOrChangeTracking() throws Exception {
        mvc.perform(get("/api/tracked")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Admin login required"));
        mvc.perform(get("/api/discovered")).andExpect(status().isUnauthorized());

        Cookie c = xsrf();
        mvc.perform(post("/api/players/Name/EUNE/track").cookie(c).header("X-XSRF-TOKEN", c.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/players/Name/EUNE/track").cookie(c).header("X-XSRF-TOKEN", c.getValue()))
                .andExpect(status().isUnauthorized());
        verify(playerService, never()).track(anyString(), anyString());
        verify(playerService, never()).untrack(anyString(), anyString());
    }

    @Test
    void anonymousPlayerViewHasNoTrackingInfo() throws Exception {
        mvc.perform(get("/api/players/Name/EUNE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameName").value("Name"))
                .andExpect(jsonPath("$.tracked").value(false))
                .andExpect(jsonPath("$.trackingLevel").value(nullValue()))
                .andExpect(jsonPath("$.discoveryDepth").value(nullValue()))
                .andExpect(jsonPath("$.nextCheckAt").value(nullValue()));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        login("admin", "nope", 401);
        login("someone", PASSWORD, 401);
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("CSRF")));
    }

    @Test
    void adminSessionUnlocksTrackingAndLogoutEndsIt() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();
        Cookie c = xsrf();
        MvcResult r = mvc.perform(post("/api/auth/login").session(before)
                        .cookie(c).header("X-XSRF-TOKEN", c.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        assertNotNull(session);
        assertNotEquals(idBefore, session.getId(), "session id rotated on login");
        Cookie rotated = r.getResponse().getCookie("XSRF-TOKEN");
        assertNotEquals(c.getValue(), rotated.getValue(), "CSRF token rotated on login");
        assertTrue(rotated.getSecure());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(get("/api/tracked").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/discovered").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/players/Name/EUNE").session(session))
                .andExpect(jsonPath("$.tracked").value(true))
                .andExpect(jsonPath("$.trackingLevel").value("TRACKED"));

        // Mutations still need the CSRF header even when logged in.
        mvc.perform(post("/api/players/Name/EUNE/track").session(session).cookie(rotated))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/players/Name/EUNE/track").session(session)
                        .cookie(rotated).header("X-XSRF-TOKEN", rotated.getValue()))
                .andExpect(status().isOk());
        verify(playerService).track("Name", "EUNE");

        mvc.perform(post("/api/auth/logout").session(session)
                        .cookie(rotated).header("X-XSRF-TOKEN", rotated.getValue()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid(), "session invalidated on logout");
        mvc.perform(get("/api/tracked")).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailuresLockLoginTemporarily() throws Exception {
        login("admin", "bad1", 401);
        login("admin", "bad2", 401);
        login("admin", "bad3", 401);
        // Even the right password is refused while locked out.
        MvcResult r = login("admin", PASSWORD, 429);
        assertNotNull(r.getResponse().getHeader("Retry-After"));
    }

    @Test
    void anonymousRequestsDoNotCreateSessions() throws Exception {
        mvc.perform(get("/api/players/Name/EUNE"))
                .andExpect(r -> assertTrue(r.getRequest().getSession(false) == null));
        mvc.perform(get("/api/tracked"))
                .andExpect(r -> assertTrue(r.getRequest().getSession(false) == null));
    }
}
