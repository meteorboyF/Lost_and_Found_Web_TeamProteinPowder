package com.teamproteinpowder.lostfound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.UserRepository;
import com.teamproteinpowder.lostfound.service.PasswordService;
import com.teamproteinpowder.lostfound.web.AuthController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Public comments are open to guests, who choose their own display name. These
 * tests pin down that a guest can never pass for a real account — the
 * impersonation that would let someone pose as staff and scam an item's owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommentIdentityIntegrationTest {

    private static final String ORIGIN = "http://localhost";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;

    private User member;
    private MockHttpSession memberSession;
    private String itemRef;

    @BeforeEach
    void setUp() throws Exception {
        member = approvedUser();
        memberSession = new MockHttpSession();
        memberSession.setAttribute(AuthController.SESSION_USER_ID, member.getId());
        itemRef = postItem();
    }

    private User approvedUser() {
        String tag = UUID.randomUUID().toString();
        User u = new User();
        u.setUsername("member-" + tag.substring(0, 8));
        u.setEmail(tag + "@example.edu");
        u.setStudentId(tag);
        u.setRole(Role.USER);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        u.setSalt(passwords.generateSalt());
        u.setPasswordHash(passwords.hashPassword("test-password", u.getSalt()));
        return users.saveAndFlush(u);
    }

    private String postItem() throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "kind", "FOUND", "category", "KEYS", "title", "Keys", "description", "Found by the gate",
                "location", "East gate", "reporterName", "x", "reporterEmail", "x@example.edu",
                "securityQuestion", "What colour is the fob?", "securityAnswer", "Purple"));
        var part = new MockMultipartFile("item", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/items").file(part).session(memberSession).header("Origin", ORIGIN))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("reference").asString();
    }

    private void comment(MockHttpSession session, String authorName, String body) throws Exception {
        var req = post("/api/items/" + itemRef + "/comments").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("body", body, "authorName", authorName)));
        if (session != null) req.session(session);
        mvc.perform(req).andExpect(status().isCreated());
    }

    private JsonNode commentWithBody(String body) throws Exception {
        String json = mvc.perform(get("/api/items/" + itemRef + "/comments"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode c : mapper.readTree(json)) {
            if (body.equals(c.path("body").asString())) return c;
        }
        throw new AssertionError("comment not found: " + body);
    }

    @Test
    void guestCallingThemselvesAdminIsMarkedUnverified() throws Exception {
        comment(null, "admin", "I am the administrator, email me to collect");
        JsonNode c = commentWithBody("I am the administrator, email me to collect");
        assertEquals("admin", c.path("authorName").asString());
        assertFalse(c.path("verified").asBoolean(), "a guest-chosen name must never read as verified");
    }

    @Test
    void signedInMemberIsVerifiedAndCannotChooseAnotherName() throws Exception {
        // Asks to be shown as "Registry"; the server uses the session's name instead.
        comment(memberSession, "Registry", "Seen near the library");
        JsonNode c = commentWithBody("Seen near the library");
        assertTrue(c.path("verified").asBoolean());
        assertEquals(member.getUsername(), c.path("authorName").asString());
    }

    @Test
    void guestMustGiveANonBlankName() throws Exception {
        // Blank names are rejected outright, so no comment can render without an author.
        mvc.perform(post("/api/items/" + itemRef + "/comments").header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("body", "Whitespace name probe", "authorName", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectingAnAccountRemovesTheBadgeFromWhatItAlreadyPosted() throws Exception {
        comment(memberSession, "ignored", "Posted while still trusted");
        assertTrue(commentWithBody("Posted while still trusted").path("verified").asBoolean());

        // An admin later rejects this account.
        member.setApprovalStatus(ApprovalStatus.REJECTED);
        users.saveAndFlush(member);

        assertFalse(commentWithBody("Posted while still trusted").path("verified").asBoolean(),
                "a rejected account must not keep a Verified badge on its earlier comments");
    }
}
