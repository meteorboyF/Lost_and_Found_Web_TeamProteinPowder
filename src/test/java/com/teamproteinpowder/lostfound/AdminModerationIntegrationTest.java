package com.teamproteinpowder.lostfound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

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
import com.teamproteinpowder.lostfound.service.StorageService;
import com.teamproteinpowder.lostfound.web.AuthController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Moderation has to work on exactly the posts that need it: abusive or
 * mistaken posts tend to have comments and claims attached, and removing one
 * must also stop its photographs being served.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationIntegrationTest {

    private static final String ORIGIN = "http://localhost";
    private static final String ANSWER = "A green pencil";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;
    @Autowired StorageService storage;

    private User admin;
    private MockHttpSession adminSession;
    private User poster;
    private MockHttpSession posterSession;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        adminSession = sessionFor(admin);
        poster = user(Role.USER);
        posterSession = sessionFor(poster);
    }

    private User user(Role role) {
        String tag = UUID.randomUUID().toString();
        User u = new User();
        u.setUsername("u-" + tag.substring(0, 8));
        u.setEmail(tag + "@example.edu");
        u.setStudentId(tag);
        u.setRole(role);
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

    private static byte[] jpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    /** Posts an item with a public and a private photo; returns the created item. */
    private JsonNode postItemWithPhotos() throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "kind", "FOUND", "category", "ID_CARDS", "title", "Student card", "description", "Found at the gym",
                "location", "Gym", "reporterName", "x", "reporterEmail", "x@example.edu",
                "securityQuestion", "What name is printed?", "securityAnswer", ANSWER));
        String body = mvc.perform(multipart("/api/items")
                        .file(new MockMultipartFile("item", "", "application/json", json.getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", jpeg()))
                        .file(new MockMultipartFile("privatePhoto", "b.jpg", "image/jpeg", jpeg()))
                        .session(posterSession).header("Origin", ORIGIN))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private int removeItem(String ref, MockHttpSession session) throws Exception {
        return mvc.perform(delete("/api/admin/items/" + ref).session(session).header("Origin", ORIGIN))
                .andReturn().getResponse().getStatus();
    }

    /* ---------------------------------------------------------------- items */

    @Test
    void anItemWithCommentsAndClaimsCanStillBeRemoved() throws Exception {
        // Before the fix the foreign keys refused, and the admin saw a vague 409.
        JsonNode item = postItemWithPhotos();
        String ref = item.path("reference").asString();
        String photoUrl = item.path("photoUrl").asString();
        Path publicFile = storage.getRoot().resolve(photoUrl.substring("/uploads/".length()));
        assertTrue(Files.exists(publicFile));

        mvc.perform(post("/api/items/" + ref + "/comments").header("Origin", ORIGIN)
                        .with(r -> { r.setRemoteAddr("198.51.100.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("body", "That is mine!", "authorName", "Guest"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/items/" + ref + "/claims").session(sessionFor(user(Role.USER))).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "claimantName", "c", "claimantEmail", "c@example.edu",
                                "proof", "My name and photo are on the front of it", "securityAnswer", ANSWER))))
                .andExpect(status().isCreated());

        assertEquals(200, removeItem(ref, adminSession));

        mvc.perform(get("/api/items/" + ref)).andExpect(status().isNotFound());
        // A removed post must not keep serving its photo from a known URL.
        assertFalse(Files.exists(publicFile), "the public photo file outlived its post");
        mvc.perform(get(photoUrl)).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanRemoveItems() throws Exception {
        String ref = postItemWithPhotos().path("reference").asString();
        assertEquals(401, removeItem(ref, posterSession));
        mvc.perform(get("/api/items/" + ref)).andExpect(status().isOk());
    }

    /* ---------------------------------------------------------------- users */

    private int userAction(String method, String path, MockHttpSession session) throws Exception {
        var req = method.equals("DELETE") ? delete(path) : post(path);
        return mvc.perform(req.session(session).header("Origin", ORIGIN)).andReturn().getResponse().getStatus();
    }

    @Test
    void anAdminCannotLockThemselvesOut() throws Exception {
        assertEquals(409, userAction("POST", "/api/admin/users/" + admin.getId() + "/reject", adminSession));
        assertEquals(409, userAction("DELETE", "/api/admin/users/" + admin.getId(), adminSession));
        assertEquals(ApprovalStatus.APPROVED, users.findById(admin.getId()).orElseThrow().getApprovalStatus());
    }

    @Test
    void deletingAnAccountWithHistoryExplainsTheAlternative() throws Exception {
        postItemWithPhotos(); // the poster now has a post
        String body = mvc.perform(delete("/api/admin/users/" + poster.getId()).session(adminSession).header("Origin", ORIGIN))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertTrue(mapper.readTree(body).path("message").asString().contains("Reject"),
                "the admin should be pointed at Reject, not given a database error: " + body);
        assertTrue(users.findById(poster.getId()).isPresent());
    }

    @Test
    void anAccountWithNoHistoryCanBeDeleted() throws Exception {
        User unused = user(Role.USER);
        assertEquals(200, userAction("DELETE", "/api/admin/users/" + unused.getId(), adminSession));
        assertFalse(users.findById(unused.getId()).isPresent());
    }
}
