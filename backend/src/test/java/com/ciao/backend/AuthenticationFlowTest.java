package com.ciao.backend;

import com.ciao.backend.entity.*;
import com.ciao.backend.repository.*;
import com.ciao.backend.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AuthenticationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtils jwt;

    private User account(String role) {
        User user = new User();
        user.setFullName("Auth test");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("0778765432");
        user.setPasswordHash(encoder.encode(" Test password "));
        user.setRole(roles.findByRoleName(role).orElseGet(() -> roles.save(new Role(null, role))));
        return users.save(user);
    }
    private Cookie login(String identifier, String expectedRole) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", identifier, "password", " Test password "))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value(expectedRole)).andReturn();
        Cookie cookie = result.getResponse().getCookie("ciao_jwt");
        assertNotNull(cookie); assertTrue(cookie.isHttpOnly()); assertEquals("/api", cookie.getPath());
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value(expectedRole));
        return cookie;
    }
    @Test void emailCaseSpacesAndPhoneFormatsKeepTheSession() throws Exception {
        User user = account("PASSENGER");
        login(" " + user.getEmail().toUpperCase() + " ", "ROLE_PASSENGER");
        for (String phone : new String[]{"0778765432", "+94 77 876 5432", "94778765432", "778765432"})
            login(phone, "ROLE_PASSENGER");
    }
    @Test void legacyPrefixedAdminRoleMatchesLoginAndMe() throws Exception {
        User admin = account("ROLE_ADMIN");
        Cookie cookie = login(admin.getEmail(), "ROLE_ADMIN");
        mvc.perform(get("/api/fleet/drivers").cookie(cookie)).andExpect(status().isOk());
    }
    @Test void passengerCannotAccessStaffDataAndLogoutExpiresCookie() throws Exception {
        User user = account("PASSENGER"); Cookie cookie = login(user.getEmail(), "ROLE_PASSENGER");
        mvc.perform(get("/api/fleet/drivers").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isOk())
            .andExpect(cookie().maxAge("ciao_jwt", 0));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void invalidAndGuestTokensDoNotAuthenticate() throws Exception {
        for (String value : new String[]{"broken", jwt.generateGuestReservationToken(1)})
            mvc.perform(get("/api/auth/me").cookie(new Cookie("ciao_jwt", value)))
                .andExpect(status().isUnauthorized());
    }
    @Test void invalidLoginHasActionableJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"missing@example.test\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password."));
    }
    @Test void registrationNormalizesAndRejectsDuplicateMobileFormats() throws Exception {
        var input = Map.of("fullName", " Test User ", "email", " new@example.test ", "phone", "+94 77 987 6543",
            "nic", "200012345678", "password", " Test password ");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isOk());
        login("0779876543", "ROLE_PASSENGER");
        var duplicate = new java.util.HashMap<>(input);
        duplicate.put("email", "another@example.test"); duplicate.put("nic", "200012345679");
        duplicate.put("phone", "0779876543");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(duplicate)))
            .andExpect(status().isBadRequest());
    }
}
