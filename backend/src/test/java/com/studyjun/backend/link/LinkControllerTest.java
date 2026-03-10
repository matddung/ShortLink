package com.studyjun.backend.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyjun.backend.auth.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void anonymousShortenAndRedirectSuccess() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/links/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest("https://example.com/very/long/path"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/very/long/path"))
                .andExpect(jsonPath("$.data.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.data.shortUrl").value(containsString("/s/")))
                .andExpect(header().string("Set-Cookie", containsString("anonymous_owner=")))
                .andReturn();

        String shortCode = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("shortCode")
                .asText();

        mockMvc.perform(get("/s/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/very/long/path"));
    }

    @Test
    void anonymousShortenInvalidUrlFails() throws Exception {
        mockMvc.perform(post("/api/links/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest("not-valid-url"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    @Test
    void anonymousLinksAreScopedByOwnerCookie() throws Exception {
        MockCookie ownerA = new MockCookie("anonymous_owner", "owner-a");
        MockCookie ownerB = new MockCookie("anonymous_owner", "owner-b");

        createAnonymous("https://a.example.com/1", ownerA);
        createAnonymous("https://a.example.com/2", ownerA);
        createAnonymous("https://b.example.com/1", ownerB);

        mockMvc.perform(get("/api/links/anonymous").cookie(ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/links/anonymous").cookie(ownerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/links/anonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void signupClaimsAnonymousLinksToUser() throws Exception {
        MockCookie owner = new MockCookie("anonymous_owner", "owner-signup");
        createAnonymous("https://claim.example.com/1", owner);
        createAnonymous("https://claim.example.com/2", owner);

        String email = "claim-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/signup")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "claimer"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("anonymous_owner=")));

        mockMvc.perform(get("/api/links/anonymous").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = json.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/links")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }


    @Test
    void authenticatedUserCanCreateLinkAndRedirect() throws Exception {
        String email = "create-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "creator"))))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        MvcResult createResult = mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://dashboard.example.com/path\",\"customCode\":\"dash-link-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortUrl").value(containsString("/s/dash-link-01")))
                .andReturn();

        String shortCode = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("shortCode")
                .asText();

        mockMvc.perform(get("/s/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://dashboard.example.com/path"));
    }

    private void createAnonymous(String url, MockCookie owner) throws Exception {
        mockMvc.perform(post("/api/links/anonymous")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest(url))))
                .andExpect(status().isOk());
    }
}