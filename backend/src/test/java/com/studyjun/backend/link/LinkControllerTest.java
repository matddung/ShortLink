package com.studyjun.backend.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyjun.backend.link.api.LinkRequest;
import com.studyjun.backend.link.domain.ShortLink;
import com.studyjun.backend.link.application.redirect.ClickEventPublisher;
import com.studyjun.backend.link.application.redirect.RedirectClickEventMessage;
import com.studyjun.backend.link.infrastructure.persistence.ShortLinkRepository;
import com.studyjun.backend.analytics.persistence.LinkClickEvent;
import com.studyjun.backend.analytics.persistence.LinkClickEventRepository;
import com.studyjun.backend.auth.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ShortLinkRepository shortLinkRepository;

    @Autowired
    LinkClickEventRepository linkClickEventRepository;

    @MockBean
    ClickEventPublisher clickEventPublisher;

    @Test
    void anonymousShortenAndRedirectSuccess() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/links/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest("https://example.com/very/long/path"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/very/long/path"))
                .andExpect(jsonPath("$.data.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.data.shortUrl").value(containsString("/api/s/")))
                .andExpect(header().string("Set-Cookie", containsString("anonymous_owner=")))
                .andReturn();

        String shortCode = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("shortCode")
                .asText();

        mockMvc.perform(get("/api/s/{shortCode}", shortCode)
                        .header("X-Request-Id", "req-anonymous-redirect"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/very/long/path"));

        ArgumentCaptor<RedirectClickEventMessage> eventCaptor = ArgumentCaptor.forClass(RedirectClickEventMessage.class);
        verify(clickEventPublisher, times(1)).publish(eventCaptor.capture());

        RedirectClickEventMessage eventMessage = eventCaptor.getValue();
        assertThat(eventMessage.eventId()).isNotNull();
        assertThat(Instant.parse(eventMessage.clickedAt())).isNotNull();
        assertThat(eventMessage.requestId()).isEqualTo("req-anonymous-redirect");
        assertThat(eventMessage.source()).isEqualTo("shortlink-backend");
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
    void repeatedRedirectWithSameRequestIdPublishesSameEventId() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/links/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest("https://example.com/idempotent/path"))))
                .andExpect(status().isOk())
                .andReturn();

        String shortCode = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("shortCode")
                .asText();

        mockMvc.perform(get("/api/s/{shortCode}", shortCode)
                        .header("X-Request-Id", "req-idempotent-redirect"))
                .andExpect(status().isFound());

        mockMvc.perform(get("/api/s/{shortCode}", shortCode)
                        .header("X-Request-Id", "req-idempotent-redirect"))
                .andExpect(status().isFound());

        ArgumentCaptor<RedirectClickEventMessage> eventCaptor = ArgumentCaptor.forClass(RedirectClickEventMessage.class);
        verify(clickEventPublisher, times(2)).publish(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(RedirectClickEventMessage::eventId)
                .containsOnly(eventCaptor.getAllValues().get(0).eventId());
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.originalUrl").value("https://dashboard.example.com/path"))
                .andExpect(jsonPath("$.data.shortCode").value("dash-link-01"))
                .andExpect(jsonPath("$.data.shortUrl").value(containsString("/s/dash-link-01")))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.totalClicks").value(0))
                .andExpect(jsonPath("$.data.userId").isString())
                .andReturn();

        String shortCode = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("shortCode")
                .asText();

        mockMvc.perform(get("/api/s/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://dashboard.example.com/path"));

        ArgumentCaptor<RedirectClickEventMessage> eventCaptor = ArgumentCaptor.forClass(RedirectClickEventMessage.class);
        verify(clickEventPublisher, times(1)).publish(eventCaptor.capture());

        RedirectClickEventMessage eventMessage = eventCaptor.getValue();
        assertThat(eventMessage.requestId()).isNotBlank().isNotEqualTo("0");
        assertThatCode(() -> UUID.fromString(eventMessage.requestId())).doesNotThrowAnyException();
    }

    @Test
    void authenticatedUserCanDeactivateAndReactivateOwnLink() throws Exception {
        String email = "status-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "status-user"))))
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
                        .content("{\"originalUrl\":\"https://status.example.com/path\",\"customCode\":\"status-link-01\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        String linkId = created.path("id").asText();
        String shortCode = created.path("shortCode").asText();

        mockMvc.perform(patch("/api/links/{id}/status", linkId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("inactive"));

        mockMvc.perform(get("/api/s/{shortCode}", shortCode))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/links/{id}/status", linkId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        mockMvc.perform(get("/api/s/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://status.example.com/path"));
    }

    @Test
    void updateStatusRejectsInvalidStatusContract() throws Exception {
        String email = "invalid-status-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "invalid-status-user"))))
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
                        .content("{\"originalUrl\":\"https://invalid-status.example.com/path\",\"customCode\":\"invalid-status-link-01\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String linkId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();

        mockMvc.perform(patch("/api/links/{id}/status", linkId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paused\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("status must be active or inactive")));
    }

    @Test
    void authenticatedUserCannotUpdateAnotherUsersLinkStatus() throws Exception {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(ownerEmail, "password123", "owner-user"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(otherEmail, "password123", "other-user"))))
                .andExpect(status().isOk());

        MvcResult ownerLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.LoginRequest(ownerEmail, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult otherLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.LoginRequest(otherEmail, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String ownerAccessToken = objectMapper.readTree(ownerLoginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        String otherAccessToken = objectMapper.readTree(otherLoginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        MvcResult createResult = mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://owner-status.example.com/path\",\"customCode\":\"owner-status-link-01\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        String linkId = created.path("id").asText();
        String shortCode = created.path("shortCode").asText();

        mockMvc.perform(patch("/api/links/{id}/status", linkId)
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"inactive\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));

        mockMvc.perform(get("/api/s/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://owner-status.example.com/path"));
    }

    @Test
    void linkStatsIncludesCountryAndReferrerAggregation() throws Exception {
        String email = "stats-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "stats-user"))))
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
                        .content("{\"originalUrl\":\"https://stats.example.com/path\",\"customCode\":\"stats-link-01\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        String linkId = created.path("id").asText();

        persistClickEvent(Long.valueOf(linkId), "KR", "https://search.example.com/result", "visitor-1");
        persistClickEvent(Long.valueOf(linkId), "US", "https://social.example.com/post", "visitor-2");

        mockMvc.perform(get("/api/links/{id}/stats", linkId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalClicks").value(2))
                .andExpect(jsonPath("$.data.uniqueClicks").value(2))
                .andExpect(jsonPath("$.data.lastClickedAt").isString())
                .andExpect(jsonPath("$.data.topCountries[0].country").exists())
                .andExpect(jsonPath("$.data.topCountries[0].count").isNumber())
                .andExpect(jsonPath("$.data.referrers[0].source").exists())
                .andExpect(jsonPath("$.data.referrers[0].count").isNumber())
                .andExpect(jsonPath("$.data.referrers[0].percentage").isNumber())
                .andExpect(jsonPath("$.data.dailyClicks[0].date").isString())
                .andExpect(jsonPath("$.data.dailyClicks[0].clicks").isNumber())
                .andExpect(jsonPath("$.data.dailyClicks.length()").value(14));
    }


    @Test
    void countryCodeFallsBackToAcceptLanguageWhenGeoHeadersMissing() throws Exception {
        String email = "lang-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "lang-user"))))
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
                        .content("{\"originalUrl\":\"https://lang.example.com/path\",\"customCode\":\"lang-link-01\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        String shortCode = created.path("shortCode").asText();

        mockMvc.perform(get("/api/s/{shortCode}", shortCode)
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
                        .header("User-Agent", "lang-agent")
                        .header("X-Forwarded-For", "203.0.113.30"))
                .andExpect(status().isFound());

        ArgumentCaptor<RedirectClickEventMessage> eventCaptor = ArgumentCaptor.forClass(RedirectClickEventMessage.class);
        verify(clickEventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().countryCode()).isEqualTo("KR");
    }


    @Test
    void countryCodeFallsBackToLanguageMappingWhenAcceptLanguageHasNoRegion() throws Exception {
        String email = "lang-only-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest.SignupRequest(email, "password123", "lang-only-user"))))
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
                        .content("{\"originalUrl\":\"https://lang-only.example.com/path\",\"customCode\":\"lang-only-link-01\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        String shortCode = created.path("shortCode").asText();

        mockMvc.perform(get("/api/s/{shortCode}", shortCode)
                        .header("Accept-Language", "ko")
                        .header("User-Agent", "lang-only-agent")
                        .header("X-Forwarded-For", "203.0.113.31"))
                .andExpect(status().isFound());

        ArgumentCaptor<RedirectClickEventMessage> eventCaptor = ArgumentCaptor.forClass(RedirectClickEventMessage.class);
        verify(clickEventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().countryCode()).isEqualTo("KR");
    }

    private void createAnonymous(String url, MockCookie owner) throws Exception {
        mockMvc.perform(post("/api/links/anonymous")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest(url))))
                .andExpect(status().isOk());
    }

    private void persistClickEvent(Long linkId, String countryCode, String referrer, String visitorKey) {
        ShortLink shortLink = shortLinkRepository.findById(linkId).orElseThrow();
        shortLink.increaseClickCount();
        shortLinkRepository.save(shortLink);
        linkClickEventRepository.save(new LinkClickEvent(
                UUID.randomUUID(),
                shortLink.getId(),
                Instant.now(),
                UUID.randomUUID().toString(),
                "test-suite",
                countryCode,
                referrer,
                visitorKey
        ));
    }
}
