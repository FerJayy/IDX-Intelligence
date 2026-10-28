package com.idxintelligence.backend.auth;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthTests extends EmbeddedPostgresTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService auth;

    @MockitoBean
    private ResetMailer mailer;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM app_user").update();
    }

    /** A fresh service, so rate-limit counters from other tests don't interfere. */
    private AuthService fresh() {
        return new AuthService(jdbc, mailer, Clock.systemUTC(), "https://site.test/");
    }

    @Test
    void registerLogInAndOut() {
        AuthService service = fresh();
        AuthService.Session registered = service.register("  Ayah@Example.com ", "rahasia123", "ip:1");
        assertThat(registered.user().email()).isEqualTo("ayah@example.com");
        assertThat(service.user(registered.token())).isPresent();
        // Only the hash is stored.
        assertThat(jdbc.sql("SELECT count(*) FROM user_session WHERE token_hash = ?").param(registered.token())
                .query(Integer.class).single()).isZero();

        AuthService.Session login = service.login("ayah@example.com", "rahasia123", "ip:1");
        service.logout(login.token());
        assertThat(service.user(login.token())).isEmpty();
        assertThat(service.user(registered.token())).isPresent();   // other devices stay logged in
    }

    @Test
    void refusesBadInputDuplicatesAndWrongPasswords() {
        AuthService service = fresh();
        assertThatThrownBy(() -> service.register("not-an-email", "rahasia123", "ip:1")).hasMessageContaining("INVALID_EMAIL");
        assertThatThrownBy(() -> service.register("a@b.co", "short", "ip:1")).hasMessageContaining("PASSWORD_TOO_SHORT");
        service.register("a@b.co", "rahasia123", "ip:1");
        assertThatThrownBy(() -> service.register("A@B.CO", "rahasia123", "ip:1")).hasMessageContaining("EMAIL_TAKEN");
        assertThatThrownBy(() -> service.login("a@b.co", "salah12345", "ip:1")).hasMessageContaining("WRONG_LOGIN");
        assertThatThrownBy(() -> service.login("nobody@b.co", "salah12345", "ip:1")).hasMessageContaining("WRONG_LOGIN");
    }

    @Test
    void repeatedWrongPasswordsAreLockedOutForAWhile() {
        AuthService service = fresh();
        service.register("a@b.co", "rahasia123", "ip:1");
        for (int i = 0; i < 8; i++) {
            assertThatThrownBy(() -> service.login("a@b.co", "salah12345", "ip:" + Math.random()))
                    .hasMessageContaining("WRONG_LOGIN");
        }
        // Even the right password is refused until the window passes.
        assertThatThrownBy(() -> service.login("a@b.co", "rahasia123", "ip:9")).hasMessageContaining("TOO_MANY_ATTEMPTS");
    }

    @Test
    void resetLinkWorksOnceAndSignsOutEverywhere() {
        AuthService service = fresh();
        AuthService.Session session = service.register("a@b.co", "rahasia123", "ip:1");

        service.requestReset("A@B.co", "ip:1", true);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendReset(eq("a@b.co"), link.capture(), eq(true));
        assertThat(link.getValue()).startsWith("https://site.test/id/reset-password?token=");
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        service.resetPassword(token, "barusekali9");
        assertThat(service.user(session.token())).isEmpty();
        assertThat(service.login("a@b.co", "barusekali9", "ip:1").user().email()).isEqualTo("a@b.co");
        assertThatThrownBy(() -> service.resetPassword(token, "lagilagi99")).hasMessageContaining("INVALID_LINK");
    }

    @Test
    void resetForUnknownEmailLooksTheSameButSendsNothing() {
        AuthService service = fresh();
        service.requestReset("nobody@b.co", "ip:1", false);
        verify(mailer, never()).sendReset(anyString(), anyString(), anyBoolean());
    }

    @Test
    void dataNeedsLoginButHealthAndAccountEndpointsDont() throws Exception {
        mockMvc.perform(get("/api/market/overview")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("LOGIN_REQUIRED"));
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@y.co\",\"password\":\"wrongwrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("WRONG_LOGIN"));

        String token = auth.register("me@b.co", "rahasia123", "ip:test").token();
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("me@b.co"));
        mockMvc.perform(put("/api/me/watchlist/bbca").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tickers[0]").value("BBCA"));
        mockMvc.perform(put("/api/me/watchlist/ZZZZ").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("UNKNOWN_STOCK"));
        mockMvc.perform(get("/api/market/overview").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsLongerThanBcryptCanCheckAreRefused() {
        assertThatThrownBy(() -> fresh().register("a@b.co", "x".repeat(73), "ip:1"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PASSWORD_TOO_LONG");
    }
}
