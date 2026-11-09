package com.notemind.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notemind.entity.User;
import com.notemind.repository.UserRepository;
import com.notemind.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    }

    @Test
    void registrationStoresAHashAndAlwaysCreatesAStudent() throws Exception {
        Session session = register();
        User user = users.findById(session.id()).orElseThrow();

        assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(user.getRole().name()).isEqualTo("STUDENT");
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void duplicateEmailIsAConflictIgnoringCase() throws Exception {
        Session session = register();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Dup", "email", session.email().toUpperCase(),
                                "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokenAndHttpOnlyRefreshCookie() throws Exception {
        Session session = register();
        MvcResult result = login(session.email(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("nm_refresh=", "HttpOnly", "SameSite=Strict", "Path=/api/auth");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("nm_refresh", "refreshToken", "passwordHash");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        Session session = register();
        MvcResult wrong = login(session.email(), "WrongPass123");
        MvcResult unknown = login("nobody@example.com", "WrongPass123");

        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(wrong.getResponse().getContentAsString()).path("message"))
                .isEqualTo(json.readTree(unknown.getResponse().getContentAsString()).path("message"));
    }

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        Session session = register();
        mvc.perform(get("/api/auth/me").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        Session session = register();
        mvc.perform(put("/api/auth/me").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void refreshRotatesTheTokenAndLogoutRevokesIt() throws Exception {
        Session session = register();
        Cookie first = refreshCookie(login(session.email(), PASSWORD));

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mvc.perform(post("/api/auth/logout").cookie(second)).andExpect(status().isNoContent());
    }

    @Test
    void refreshWithoutOrWithBadCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("nm_refresh", "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAUserInvalidatesTheirExistingTokenImmediately() throws Exception {
        Session session = register();
        User user = users.findById(session.id()).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);

        mvc.perform(get("/api/auth/me").header("Authorization", session.bearer())).andExpect(status().isUnauthorized());
        assertThat(login(session.email(), PASSWORD).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void changePasswordRequiresTheCurrentOne() throws Exception {
        Session session = register();
        mvc.perform(post("/api/auth/me/password").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "Wrong12345", "newPassword", "Newpass123"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/me/password").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "Newpass123"))))
                .andExpect(status().isNoContent());
        assertThat(login(session.email(), "Newpass123").getResponse().getStatus()).isEqualTo(200);
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("nm_refresh");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
