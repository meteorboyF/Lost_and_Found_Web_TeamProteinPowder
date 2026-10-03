package com.teamproteinpowder.lostfound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.UserRepository;
import com.teamproteinpowder.lostfound.service.PasswordService;
import com.teamproteinpowder.lostfound.service.RateLimiter;
import com.teamproteinpowder.lostfound.web.AuthController;

import tools.jackson.databind.ObjectMapper;

/**
 * Posting a comment is rate limited; reading comments must never be.
 *
 * Written after the limiter was briefly wired into the read endpoint by
 * mistake: posting was then unlimited, while every item page view counted
 * against the limit — on a shared campus address the comments section would
 * have started failing for everyone after a handful of views. The existing
 * suite passed throughout, because nothing read comments more than ten times
 * from one address. These tests close that gap from both sides.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommentRateLimitIntegrationTest {

    private static final String ORIGIN = "http://localhost";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;

    /** A fresh client per test, so the shared limiter's budgets don't leak between tests. */
    private final String clientAddress = "172.16.%d.%d".formatted(
            (int) (Math.random() * 250) + 1, (int) (Math.random() * 250) + 1);

    private String itemRef;

    private RequestPostProcessor from(String address) {
        return request -> { request.setRemoteAddr(address); return request; };
    }

    @BeforeEach
    void setUp() throws Exception {
        MockHttpSession poster = sessionFor(approvedUser());
        String json = mapper.writeValueAsString(Map.of(
                "kind", "FOUND", "category", "BAGS", "title", "Tote", "description", "Found in the hall",
                "location", "Hall", "reporterName", "x", "reporterEmail", "x@example.edu",
                "securityQuestion", "What is in the pocket?", "securityAnswer", "A pencil"));
        var part = new MockMultipartFile("item", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/items").file(part).session(poster).header("Origin", ORIGIN))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        itemRef = mapper.readTree(body).path("reference").asString();
    }

    private User approvedUser() {
        String tag = UUID.randomUUID().toString();
        User u = new User();
        u.setUsername("u-" + tag.substring(0, 8));
        u.setEmail(tag + "@example.edu");
        u.setStudentId(tag);
        u.setRole(Role.USER);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        u.setSalt(passwords.generateSalt());
        u.setPasswordHash(passwords.hashPassword("test-password", u.getSalt()));
        return users.saveAndFlush(u);
    }

    private MockHttpSession sessionFor(User u) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(AuthController.SESSION_USER_ID, u.getId());
        return s;
    }

    private int postComment(MockHttpSession session, String address, int n) throws Exception {
        var req = post("/api/items/" + itemRef + "/comments").header("Origin", ORIGIN).with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("body", "comment " + n, "authorName", "Guest")));
        if (session != null) req.session(session);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void readingCommentsIsNeverThrottled() throws Exception {
        // Far past every comment limit. Each item page view makes this call.
        int views = RateLimiter.MEMBER_COMMENT.max() * 3;
        for (int i = 0; i < views; i++) {
            mvc.perform(get("/api/items/" + itemRef + "/comments").with(from(clientAddress)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void guestPostingIsLimitedPerAddress() throws Exception {
        int max = RateLimiter.GUEST_COMMENT.max();
        for (int i = 0; i < max; i++) {
            assertEquals(201, postComment(null, clientAddress, i), "comment " + (i + 1) + " should be allowed");
        }
        assertEquals(429, postComment(null, clientAddress, max), "one past the guest limit must be refused");
    }

    @Test
    void membersOnOneSharedAddressEachGetTheirOwnLimit() throws Exception {
        // Two students on the same campus NAT: one using up their allowance
        // must not silence the other.
        MockHttpSession alice = sessionFor(approvedUser());
        MockHttpSession bob = sessionFor(approvedUser());
        for (int i = 0; i < RateLimiter.MEMBER_COMMENT.max(); i++) {
            assertEquals(201, postComment(alice, clientAddress, i));
        }
        assertEquals(429, postComment(alice, clientAddress, 999));
        assertEquals(201, postComment(bob, clientAddress, 0), "a different member on the same address is unaffected");
    }
}
