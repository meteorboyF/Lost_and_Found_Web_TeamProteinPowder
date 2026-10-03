package com.teamproteinpowder.lostfound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * A poster closing their own report, and searching the board for text that
 * contains SQL wildcard characters.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemLifecycleIntegrationTest {

    private static final String ORIGIN = "http://localhost";
    private static final String ANSWER = "A green pencil";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;

    private MockHttpSession poster;
    private MockHttpSession claimant;

    @BeforeEach
    void setUp() {
        poster = sessionFor(approvedUser());
        claimant = sessionFor(approvedUser());
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

    private String postItem(String title) throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "kind", "FOUND", "category", "BAGS", "title", title, "description", "Left on a bench",
                "location", "Quad", "reporterName", "x", "reporterEmail", "x@example.edu",
                "securityQuestion", "What is in the front pocket?", "securityAnswer", ANSWER));
        var part = new MockMultipartFile("item", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/items").file(part).session(poster).header("Origin", ORIGIN))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("reference").asString();
    }

    private String claim(String itemRef) throws Exception {
        String body = mvc.perform(post("/api/items/" + itemRef + "/claims").session(claimant).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "claimantName", "c", "claimantEmail", "c@example.edu",
                                "proof", "It has my initials stitched inside the strap",
                                "securityAnswer", ANSWER))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("reference").asString();
    }

    private int close(String itemRef, MockHttpSession session) throws Exception {
        var req = post("/api/items/" + itemRef + "/close").header("Origin", ORIGIN);
        if (session != null) req.session(session);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private String itemStatus(String itemRef) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/items/" + itemRef)).andReturn().getResponse().getContentAsString())
                .path("status").asString();
    }

    /* ------------------------------------------------------------------ close */

    @Test
    void posterClosingEndsOpenClaimsAndStopsNewOnes() throws Exception {
        String item = postItem("Grey backpack");
        String claimRef = claim(item);
        assertEquals("PENDING", itemStatus(item));

        assertEquals(200, close(item, poster));
        assertEquals("RESOLVED", itemStatus(item));

        // The waiting claimant is told, not left hanging.
        JsonNode c = mapper.readTree(mvc.perform(get("/api/claims/" + claimRef).session(claimant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("DECLINED", c.path("status").asString());
        JsonNode messages = c.path("messages");
        assertTrue(messages.get(messages.size() - 1).path("body").asString().contains("closed this report"));

        // And nobody can open a new claim on a closed report.
        mvc.perform(post("/api/items/" + item + "/claims").session(sessionFor(approvedUser())).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "claimantName", "d", "claimantEmail", "d@example.edu",
                                "proof", "This one is definitely mine, honest", "securityAnswer", ANSWER))))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyThePosterCanClose() throws Exception {
        String item = postItem("Blue umbrella");
        assertEquals(401, close(item, null), "guests");
        assertEquals(403, close(item, claimant), "another member");
        assertEquals("OPEN", itemStatus(item));
    }

    @Test
    void closingTwiceIsAConflict() throws Exception {
        String item = postItem("Red scarf");
        assertEquals(200, close(item, poster));
        assertEquals(409, close(item, poster));
    }

    @Test
    void anApprovedPickupMustBeFinishedOrDeclinedFirst() throws Exception {
        // Closing would otherwise strand a claimant who has been told to come and collect it.
        String item = postItem("Laptop sleeve");
        String claimRef = claim(item);
        mvc.perform(post("/api/claims/" + claimRef + "/verify").session(poster).header("Origin", ORIGIN))
                .andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + claimRef + "/accept").session(poster).header("Origin", ORIGIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        assertEquals(409, close(item, poster));
        assertEquals("PENDING", itemStatus(item));
    }

    /* ----------------------------------------------------------------- search */

    private JsonNode search(String q) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/items").param("q", q).param("size", "60"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content");
    }

    @Test
    void wildcardCharactersInASearchMatchLiterally() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        postItem("Charger " + tag + " 100% charged");
        postItem("Charger " + tag + " flat");

        JsonNode percent = search(tag + " 100%");
        assertEquals(1, percent.size(), "a literal % finds exactly the item containing it");

        // Before the fix, "_" and "%" were wildcards and returned the whole board.
        for (JsonNode item : search("_")) {
            String text = item.path("title").asString() + item.path("description").asString()
                    + item.path("location").asString() + item.path("colour").asString();
            assertTrue(text.contains("_"), "'_' matched an item without an underscore: " + item.path("title"));
        }
        for (JsonNode item : search("%")) {
            String text = item.path("title").asString() + item.path("description").asString()
                    + item.path("location").asString() + item.path("colour").asString();
            assertTrue(text.contains("%"), "'%' matched an item without a percent sign: " + item.path("title"));
        }
        assertFalse(search("%").isEmpty(), "the item that does contain % is still found");
    }

    @Test
    void overlongSearchesAreRejectedClearly() throws Exception {
        mvc.perform(get("/api/items").param("q", "x".repeat(101))).andExpect(status().isBadRequest());
    }
}
