package com.studyjun.backend.link;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.anonymous.expiration-days=0")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnonymousLinkExpiryTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void expiredAnonymousLinksAreRemovedFromList() throws Exception {
        MockCookie owner = new MockCookie("anonymous_owner", "owner-expire");

        mockMvc.perform(post("/api/links/anonymous")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkRequest.CreateAnonymousRequest("https://expire.example.com/1"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/links/anonymous").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}