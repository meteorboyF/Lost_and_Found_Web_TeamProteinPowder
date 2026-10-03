package com.teamproteinpowder.lostfound;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.teamproteinpowder.lostfound.domain.*;
import com.teamproteinpowder.lostfound.repo.*;
import com.teamproteinpowder.lostfound.service.PasswordService;
import com.teamproteinpowder.lostfound.web.AuthController;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PrivacyAndChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ItemRepository items;
    @Autowired ClaimRepository claims;
    @Autowired CommentRepository comments;
    @Autowired PasswordService passwords;
    @Autowired ClaimAuditRepository audit;
    User poster, claimant, stranger, admin;
    MockHttpSession posterSession, claimantSession, strangerSession, adminSession;

    @BeforeEach
    void setup() {
        poster = user(Role.USER);
        claimant = user(Role.USER);
        stranger = user(Role.USER);
        admin = user(Role.ADMIN);
        posterSession = session(poster);
        claimantSession = session(claimant);
        strangerSession = session(stranger);
        adminSession = session(admin);
    }

    @Test
    void publicAndPrivatePhotosHaveDifferentPermissionsAndNeverExposeSecrets() throws Exception {
        JsonNode created = postItem(true);
        String ref = created.get("reference").asString();
        String photoUrl = created.get("photoUrl").asString();
        String response = created.toString();
        assertFalse(response.contains("privatePhotoName"));
        assertFalse(response.contains("securityAnswer"));
        assertFalse(response.contains("Hidden Sticker"));
        mvc.perform(get(photoUrl)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        String privateUrl = "/api/items/" + ref + "/private-photo";
        mvc.perform(get(privateUrl)).andExpect(status().isUnauthorized());
        mvc.perform(get(privateUrl).session(strangerSession)).andExpect(status().isForbidden());
        mvc.perform(get(privateUrl).session(posterSession)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(privateUrl).session(adminSession)).andExpect(status().isOk());
        String privateName = items.findByReference(ref).orElseThrow().getPrivatePhotoName();
        mvc.perform(get("/uploads/private/" + privateName)).andExpect(status().isNotFound());
        mvc.perform(get("/uploads/" + privateName)).andExpect(status().isNotFound());
        JsonNode claim = claim(ref, claimantSession, " hidden   sticker ");
        mvc.perform(get(privateUrl).session(claimantSession)).andExpect(status().isForbidden());
        String claimRef = claim.get("reference").asString();
        reviewAndApprove(claimRef);
        mvc.perform(get(privateUrl).session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + claimRef + "/handover").session(posterSession)).andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + claimRef + "/handover").session(claimantSession)).andExpect(status().isOk());
        mvc.perform(get(privateUrl).session(claimantSession)).andExpect(status().isOk());
        mvc.perform(get(privateUrl).session(strangerSession)).andExpect(status().isForbidden());
        Item saved = items.findByReference(ref).orElseThrow();
        assertEquals(poster.getEmail(), saved.getReporterEmail());
        assertNotEquals("Hidden Sticker", saved.getSecurityAnswerHash());
    }

    @Test
    void conversationsRequireParticipantsAndSenderRoleComesFromSession() throws Exception {
        String item = postItem(false).get("reference").asString();
        JsonNode claim = claim(item, claimantSession, "Hidden Sticker");
        String ref = claim.get("reference").asString();
        assertTrue(claim.get("chatUnlocked").asBoolean());
        assertEquals("CLAIMANT", claim.get("viewerRole").asString());
        mvc.perform(get("/api/claims/" + ref)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/" + ref).session(strangerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/items/" + item + "/claims").session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/claims?email=" + poster.getEmail()).session(strangerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/claims?refs=" + ref).session(strangerSession)).andExpect(content().json("[]"));
        mvc.perform(post("/api/claims/" + ref + "/messages").session(claimantSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hello poster\",\"fromPoster\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.author").value("CLAIMANT"))
                .andExpect(jsonPath("$.authorName").value(claimant.getUsername()));
        mvc.perform(post("/api/claims/" + ref + "/messages").session(posterSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hello claimant\",\"fromPoster\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.author").value("POSTER"));
        mvc.perform(post("/api/claims/" + ref + "/messages").session(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Impersonation\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/accept").session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/decline").session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/withdraw").session(posterSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/claims/" + ref).session(posterSession))
                .andExpect(jsonPath("$.messages.length()").value(3)).andExpect(jsonPath("$.viewerRole").value("POSTER"));
        mvc.perform(get("/api/items/mine").session(posterSession)).andExpect(jsonPath("$[0].reference").value(item));
        mvc.perform(get("/api/claims").session(claimantSession)).andExpect(jsonPath("$[0].reference").value(ref));
    }

    @Test
    void failedAnswersPersistAcrossSessionsAndDoNotCreateClaims() throws Exception {
        String ref = postItem(false).get("reference").asString();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/items/" + ref + "/claims").session(session(claimant))
                    .contentType(MediaType.APPLICATION_JSON).content(claimBody("wrong answer")))
                    .andExpect(status().isForbidden());
        }
        assertEquals(5, users.findById(claimant.getId()).orElseThrow().getSecurityFailures());
        assertEquals(0, claims.countByItemAndStatus(items.findByReference(ref).orElseThrow(), ClaimStatus.OPEN));
        mvc.perform(post("/api/items/" + ref + "/claims").session(claimantSession)
                .contentType(MediaType.APPLICATION_JSON).content(claimBody("Hidden Sticker")))
                .andExpect(status().isTooManyRequests());
        User expired = users.findById(claimant.getId()).orElseThrow();
        expired.setSecurityWindowStarted(Instant.now().minusSeconds(901));
        users.saveAndFlush(expired);
        assertTrue(claim(ref, claimantSession, "Hidden Sticker").get("chatUnlocked").asBoolean());
    }

    @Test
    void legacyProofRequiresPosterApprovalAndClosedConversationsRejectMessages() throws Exception {
        String item = postItem(false).get("reference").asString();
        Item legacy = items.findByReference(item).orElseThrow();
        legacy.setSecurityAnswerHash(null);
        legacy.setSecurityAnswerSalt(null);
        items.saveAndFlush(legacy);
        JsonNode claim = claim(item, claimantSession, "");
        String ref = claim.get("reference").asString();
        assertFalse(claim.get("chatUnlocked").asBoolean());
        mvc.perform(post("/api/claims/" + ref + "/messages").session(claimantSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Bypass attempt\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/verify").session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/verify").session(posterSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.chatUnlocked").value(true));
        mvc.perform(post("/api/claims/" + ref + "/withdraw").session(claimantSession)).andExpect(status().isOk());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(post("/api/claims/" + ref + "/messages").session(claimantSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Closed reply\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidUploadsQuestionsAndCrossOriginWritesAreRejected() throws Exception {
        for (Category category : Category.values()) {
            mvc.perform(get("/api/items/questions").param("category", category.name()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.question").isNotEmpty());
        }
        mvc.perform(multipart("/api/items").file(itemPart()).file(new MockMultipartFile("photo", "fake.png", "image/png",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).session(posterSession))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/items").file(itemPart()).file(new MockMultipartFile("photo", "real.svg", "image/svg+xml", png()))
                .session(posterSession)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/items").file(itemPart())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/items").file(itemPart()).session(posterSession).header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(posterSession).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/items").header("X-Admin-Key", "campus-admin-2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateCommentCannotBypassQuestionAndPublicCommentIdentityIsBoundToAccount() throws Exception {
        String ref = postItem(false).get("reference").asString();
        String path = "/api/items/" + ref + "/comments";
        mvc.perform(post(path).session(claimantSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Private bypass\",\"authorName\":\"Impersonated\",\"privateMessage\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).session(claimantSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Public help\",\"authorName\":\"Impersonated\",\"authorEmail\":\"fake@example.edu\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorName").value(claimant.getUsername()));
        Comment secret = new Comment();
        secret.setItem(items.findByReference(ref).orElseThrow());
        secret.setUser(claimant);
        secret.setAuthorName(claimant.getUsername());
        secret.setAuthorEmail(claimant.getEmail());
        secret.setBody("LEGACY SECRET");
        secret.setPrivateMessage(true);
        comments.saveAndFlush(secret);
        mvc.perform(get(path)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path).session(strangerSession)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path).session(posterSession)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(path).session(claimantSession)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void acceptingOneClaimClosesOtherThreadsAndDuplicateClaimsAreRejected() throws Exception {
        String item = postItem(false).get("reference").asString();
        String chosen = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        String other = claim(item, strangerSession, "Hidden Sticker").get("reference").asString();
        mvc.perform(post("/api/items/" + item + "/claims").session(claimantSession)
                .contentType(MediaType.APPLICATION_JSON).content(claimBody("Hidden Sticker")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/items/" + item + "/claims").session(posterSession)
                .contentType(MediaType.APPLICATION_JSON).content(claimBody("Hidden Sticker")))
                .andExpect(status().isBadRequest());
        reviewAndApprove(chosen);
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/claims/" + other).session(strangerSession)).andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(post("/api/claims/" + chosen + "/handover").session(posterSession)).andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + chosen + "/handover").session(claimantSession)).andExpect(status().isOk());
        mvc.perform(get("/api/claims/" + other).session(strangerSession)).andExpect(jsonPath("$.status").value("DECLINED"));
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(post("/api/items/" + item + "/claims").session(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(claimBody("Hidden Sticker")))
                .andExpect(status().isConflict());
    }

    @Test
    void decliningOneOfSeveralClaimsKeepsItemPendingUntilLastWithdrawal() throws Exception {
        String item = postItem(false).get("reference").asString();
        String first = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        String last = claim(item, strangerSession, "Hidden Sticker").get("reference").asString();
        mvc.perform(post("/api/claims/" + first + "/decline").session(posterSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Details do not match\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/claims/" + last + "/withdraw").session(strangerSession)).andExpect(status().isOk());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void accountRevocationIsAppliedToExistingSessionsAndLoginRotatesSessionId() throws Exception {
        String before = claimantSession.getId();
        mvc.perform(post("/api/auth/login").session(claimantSession).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("identifier", claimant.getEmail(), "password", "test-password"))))
                .andExpect(status().isOk());
        assertNotEquals(before, claimantSession.getId());
        claimant.setApprovalStatus(ApprovalStatus.REJECTED);
        users.saveAndFlush(claimant);
        mvc.perform(get("/api/auth/me").session(claimantSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims").session(claimantSession)).andExpect(status().isUnauthorized());
        adminSession.setAttribute(AuthController.SESSION_USER_ROLE, "ADMIN");
        admin.setRole(Role.USER);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/overview").session(adminSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").session(posterSession).header("Origin", "http://localhost"))
                .andExpect(status().isOk());
    }

    @Test
    void demoSeedingDoesNotPromoteAnExistingAccountOrReverseARejection() throws Exception {
        User existingAdminEmail = user(Role.USER);
        existingAdminEmail.setEmail("admin@campus.edu");
        existingAdminEmail.setApprovalStatus(ApprovalStatus.REJECTED);
        users.saveAndFlush(existingAdminEmail);
        User existingStudentEmail = user(Role.USER);
        existingStudentEmail.setEmail("student@campus.edu");
        existingStudentEmail.setApprovalStatus(ApprovalStatus.REJECTED);
        users.saveAndFlush(existingStudentEmail);
        var seeder = new com.teamproteinpowder.lostfound.service.SeedLoader(items, users, passwords, mapper, true, "", "");
        seeder.run(new org.springframework.boot.DefaultApplicationArguments());
        assertEquals(Role.USER, users.findById(existingAdminEmail.getId()).orElseThrow().getRole());
        assertEquals(ApprovalStatus.REJECTED, users.findById(existingAdminEmail.getId()).orElseThrow().getApprovalStatus());
        assertEquals(ApprovalStatus.REJECTED, users.findById(existingStudentEmail.getId()).orElseThrow().getApprovalStatus());
    }

    @Test
    void reportPinsRoundTripExactlyAndOnlyPosterCanAdjustOrRemoveThem() throws Exception {
        var payload = (tools.jackson.databind.node.ObjectNode) mapper.readTree(itemPart().getBytes());
        payload.put("latitude", 23.7978829);
        payload.put("longitude", 90.44971);
        payload.put("searchRadiusMeters", 50);
        var item = json(mvc.perform(multipart("/api/items").session(posterSession).file(new MockMultipartFile(
                "item", "", "application/json", mapper.writeValueAsBytes(payload)))).andExpect(status().isCreated()).andReturn());
        String ref = item.get("reference").asString();
        assertEquals(23.7978829, item.get("latitude").asDouble());
        assertEquals(90.44971, item.get("longitude").asDouble());
        assertEquals(50, item.get("searchRadiusMeters").asInt());
        String path = "/api/items/" + ref + "/location";
        String updated = "{\"location\":\"UIU library, level 2\",\"latitude\":23.7981,\"longitude\":90.4499,\"searchRadiusMeters\":25}";
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(updated)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).session(strangerSession).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).session(posterSession).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").value(23.7981))
                .andExpect(jsonPath("$.longitude").value(90.4499)).andExpect(jsonPath("$.searchRadiusMeters").value(25));
        mvc.perform(get("/api/items/" + ref)).andExpect(jsonPath("$.location").value("UIU library, level 2"))
                .andExpect(jsonPath("$.latitude").value(23.7981));
        mvc.perform(put(path).session(posterSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"UIU library, exact spot unknown\",\"latitude\":null,\"longitude\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").isEmpty())
                .andExpect(jsonPath("$.longitude").isEmpty()).andExpect(jsonPath("$.searchRadiusMeters").isEmpty());
    }

    @Test
    void invalidOrIncompleteCoordinatesAndSearchRadiiAreRejected() throws Exception {
        var payload = (tools.jackson.databind.node.ObjectNode) mapper.readTree(itemPart().getBytes());
        payload.put("latitude", 91.0); payload.put("longitude", 90.44971);
        mvc.perform(multipart("/api/items").session(posterSession).file(new MockMultipartFile(
                "item", "", "application/json", mapper.writeValueAsBytes(payload))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.latitude").exists());
        payload.put("latitude", 23.7978829); payload.remove("longitude");
        mvc.perform(multipart("/api/items").session(posterSession).file(new MockMultipartFile(
                "item", "", "application/json", mapper.writeValueAsBytes(payload))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.coordinatesPaired").exists());
        payload.put("longitude", 90.44971); payload.put("searchRadiusMeters", 501);
        mvc.perform(multipart("/api/items").session(posterSession).file(new MockMultipartFile(
                "item", "", "application/json", mapper.writeValueAsBytes(payload))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.searchRadiusMeters").exists());
        String ref = postItem(false).get("reference").asString();
        mvc.perform(put("/api/items/" + ref + "/location").session(posterSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"UIU\",\"latitude\":23.8,\"longitude\":181}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/items/" + ref + "/location").session(posterSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"UIU\",\"latitude\":23.8,\"longitude\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void privateClaimEvidenceRequiresParticipantOrStaffAndNeverAppearsOnPublicBoard() throws Exception {
        String item = postItem(false).get("reference").asString();
        JsonNode submitted = json(mvc.perform(multipart("/api/items/" + item + "/claims")
                .file(new MockMultipartFile("claim", "", "application/json", claimBody("Hidden Sticker").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("evidence", "receipt.png", "image/png", png())).session(claimantSession))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.safety.hasEvidencePhoto").value(true)).andReturn());
        String ref = submitted.get("reference").asString(), path = "/api/claims/" + ref + "/evidence";
        assertFalse(submitted.toString().contains("evidencePhotoName"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).session(strangerSession)).andExpect(status().isForbidden());
        for (MockHttpSession allowed : new MockHttpSession[]{posterSession, claimantSession, adminSession}) {
            mvc.perform(get(path).session(allowed)).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(header().string("Cache-Control", "no-store"));
        }
        String file = claims.findByReference(ref).orElseThrow().getEvidencePhotoName();
        mvc.perform(get("/uploads/" + file)).andExpect(status().isNotFound());
        mvc.perform(get("/uploads/private/" + file)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.proof").doesNotExist()).andExpect(jsonPath("$.safety").doesNotExist());
        mvc.perform(get("/api/desk/claims").session(strangerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/desk/claims/" + ref).session(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.safety.proof").value("My identifying details are inside the case."));
    }

    @Test
    void badEvidenceIsRejectedAtomicallyAndNewEvidenceInvalidatesPreviousReviews() throws Exception {
        String item = postItem(false).get("reference").asString();
        mvc.perform(multipart("/api/items/" + item + "/claims")
                .file(new MockMultipartFile("claim", "", "application/json", claimBody("Hidden Sticker").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("evidence", "fake.png", "image/png", "not an image".getBytes())).session(claimantSession))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, claims.countByItemAndStatus(items.findByReference(item).orElseThrow(), ClaimStatus.OPEN));
        String ref = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        mvc.perform(post("/api/claims/" + ref + "/verify").session(posterSession)).andExpect(status().isOk());
        deskDecision(ref, true, true).andExpect(status().isOk());
        mvc.perform(multipart("/api/claims/" + ref + "/evidence").session(posterSession)
                .file(new MockMultipartFile("evidence", "receipt.png", "image/png", png()))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/claims/" + ref + "/evidence").session(claimantSession)
                .file(new MockMultipartFile("evidence", "receipt.png", "image/png", png()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.safety.evidenceReviewedAt").isEmpty())
                .andExpect(jsonPath("$.safety.deskReviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.safety.studentIdCheckedAt").isEmpty());
        mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession)).andExpect(status().isConflict());
        mvc.perform(multipart("/api/claims/" + ref + "/evidence").session(claimantSession)
                .file(new MockMultipartFile("evidence", "receipt.png", "image/png", png()))).andExpect(status().isConflict());
    }

    @Test
    void approvalRequiresHumanEvidenceReviewAndValuableItemsRequireStaffIdCheck() throws Exception {
        String item = postItem(false).get("reference").asString();
        String ref = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession)).andExpect(status().isConflict());
        deskDecision(ref, true, false).andExpect(status().isBadRequest());
        mvc.perform(post("/api/claims/" + ref + "/verify").session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/verify").session(posterSession)).andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession)).andExpect(status().isConflict());
        mvc.perform(post("/api/desk/claims/" + ref + "/review").session(posterSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clear\":true,\"studentIdChecked\":true,\"note\":\"Impersonating staff\"}"))
                .andExpect(status().isForbidden());
        deskDecision(ref, true, true).andExpect(status().isOk()).andExpect(jsonPath("$.safety.studentIdCheckedAt").isNotEmpty());
        mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.resolvedAt").isEmpty());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/claims/" + ref + "/messages").session(claimantSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Let us arrange pickup at the desk.\"}")).andExpect(status().isOk());
    }

    @Test
    void handoverNeedsBothDistinctParticipantsAndOneAccountCannotConfirmTwice() throws Exception {
        String item = postItem(false).get("reference").asString();
        String ref = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        String other = claim(item, strangerSession, "Hidden Sticker").get("reference").asString();
        mvc.perform(post("/api/claims/" + ref + "/handover").session(claimantSession)).andExpect(status().isConflict());
        reviewAndApprove(ref);
        mvc.perform(post("/api/claims/" + ref + "/handover").session(strangerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/handover").session(adminSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/" + ref + "/handover").session(posterSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.safety.claimantHandoverAt").isEmpty());
        int events = audit.findByClaimReferenceOrderByIdAsc(ref).size();
        mvc.perform(post("/api/claims/" + ref + "/handover").session(posterSession)).andExpect(status().isOk());
        assertEquals(events, audit.findByClaimReferenceOrderByIdAsc(ref).size());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/claims/" + other + "/verify").session(posterSession)).andExpect(status().isOk());
        deskDecision(other, true, true).andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + other + "/accept").session(posterSession)).andExpect(status().isConflict());
        mvc.perform(post("/api/claims/" + ref + "/handover").session(claimantSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/claims/" + other).session(strangerSession)).andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void fraudFreezesEveryClaimAndWithdrawalCannotDismissTheDispute() throws Exception {
        String item = postItem(false).get("reference").asString();
        String ref = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        String other = claim(item, strangerSession, "Hidden Sticker").get("reference").asString();
        reviewAndApprove(ref);
        mvc.perform(post("/api/claims/" + ref + "/handover").session(posterSession)).andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + other + "/flag").session(strangerSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"The claimant cannot describe the concealed serial markings.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.safety.handoverFrozen").value(true));
        mvc.perform(get("/api/claims/" + ref).session(posterSession)).andExpect(jsonPath("$.safety.posterHandoverAt").isEmpty());
        mvc.perform(post("/api/claims/" + ref + "/handover").session(claimantSession)).andExpect(status().isConflict());
        mvc.perform(post("/api/claims/" + other + "/withdraw").session(strangerSession)).andExpect(status().isOk());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.handoverFrozen").value(true));
        deskDecision(other, false, false).andExpect(status().isOk());
        mvc.perform(get("/api/items/" + item)).andExpect(jsonPath("$.handoverFrozen").value(false));
        mvc.perform(post("/api/claims/" + ref + "/handover").session(claimantSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/claims/" + ref + "/handover").session(posterSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void allDisputesMustBeResolvedAndAuditIsPrivateAndCannotBeEditedByHttp() throws Exception {
        String item = postItem(false).get("reference").asString();
        String first = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        String second = claim(item, strangerSession, "Hidden Sticker").get("reference").asString();
        for (String ref : new String[]{first, second}) {
            mvc.perform(post("/api/claims/" + ref + "/flag").session(posterSession).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Need a staff review of conflicting evidence.\"}")).andExpect(status().isOk());
        }
        deskDecision(first, true, true).andExpect(status().isOk()).andExpect(jsonPath("$.safety.handoverFrozen").value(true));
        deskDecision(second, false, false).andExpect(status().isOk()).andExpect(jsonPath("$.safety.handoverFrozen").value(false));
        mvc.perform(get("/api/claims/" + first + "/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/" + first + "/audit").session(strangerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/desk/audit").session(claimantSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/desk/audit").session(adminSession)).andExpect(status().isOk());
        mvc.perform(delete("/api/claims/" + first + "/audit").session(adminSession)).andExpect(status().isMethodNotAllowed());
        var history = audit.findByClaimReferenceOrderByIdAsc(first);
        assertTrue(history.stream().anyMatch(event -> event.getAction().equals("FRAUD_REPORTED") && event.getActorId().equals(poster.getId())));
        assertTrue(history.stream().anyMatch(event -> event.getAction().equals("DESK_CLEARED") && event.getActorId().equals(admin.getId())));
    }

    @Test
    void staffCannotClearTheirOwnItemAndManualDeskReviewIsAvailableForOrdinaryItems() throws Exception {
        var payload = (tools.jackson.databind.node.ObjectNode) mapper.readTree(itemPart().getBytes());
        payload.put("category", "BOOKS");
        String item = json(mvc.perform(multipart("/api/items").session(posterSession).file(new MockMultipartFile("item", "", "application/json", mapper.writeValueAsBytes(payload))))
                .andExpect(status().isCreated()).andReturn()).get("reference").asString();
        String ref = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        mvc.perform(post("/api/claims/" + ref + "/desk-review").session(claimantSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"I prefer collecting this through the campus desk.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.safety.deskReviewStatus").value("PENDING"));
        poster.setRole(Role.ADMIN); users.saveAndFlush(poster);
        mvc.perform(post("/api/desk/claims/" + ref + "/review").session(posterSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clear\":true,\"studentIdChecked\":true,\"note\":\"Reviewing my own item\"}"))
                .andExpect(status().isForbidden());
        deskDecision(ref, true, true).andExpect(status().isOk());
    }

    @Test
    void ordinaryItemCanCompleteWithoutDeskButExplicitValuableFlagCannotBeBypassed() throws Exception {
        var payload = (tools.jackson.databind.node.ObjectNode) mapper.readTree(itemPart().getBytes());
        payload.put("category", "BOOKS");
        for (boolean deskRequired : new boolean[]{false, true}) {
            payload.put("deskReviewRequired", deskRequired);
            String item = json(mvc.perform(multipart("/api/items").session(posterSession).file(new MockMultipartFile("item", "", "application/json", mapper.writeValueAsBytes(payload))))
                    .andExpect(status().isCreated()).andReturn()).get("reference").asString();
            String ref = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
            mvc.perform(post("/api/claims/" + ref + "/verify").session(posterSession)).andExpect(status().isOk());
            mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession))
                    .andExpect(deskRequired ? status().isConflict() : status().isOk());
        }
    }

    private org.springframework.test.web.servlet.ResultActions deskDecision(String ref, boolean clear, boolean idChecked) throws Exception {
        return mvc.perform(post("/api/desk/claims/" + ref + "/review").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("clear", clear, "studentIdChecked", idChecked, "note", "Reviewed concealed details and matched the account in person."))));
    }

    @Test
    void concurrentApprovalsCannotReserveTwoClaimsAndConcurrentHandoverCompletesOnce() throws Exception {
        String item = postItem(false).get("reference").asString();
        String first = claim(item, claimantSession, "Hidden Sticker").get("reference").asString();
        String second = claim(item, strangerSession, "Hidden Sticker").get("reference").asString();
        for (String ref : new String[]{first, second}) {
            mvc.perform(post("/api/claims/" + ref + "/verify").session(posterSession)).andExpect(status().isOk());
            deskDecision(ref, true, true).andExpect(status().isOk());
        }
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> { start.await(); return mvc.perform(post("/api/claims/" + first + "/accept").session(posterSession)).andReturn().getResponse().getStatus(); });
            var b = workers.submit(() -> { start.await(); return mvc.perform(post("/api/claims/" + second + "/accept").session(posterSession)).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertEquals(java.util.Set.of(200, 409), java.util.Set.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS)));
        }
        assertEquals(1, claims.countByItemAndStatus(items.findByReference(item).orElseThrow(), ClaimStatus.APPROVED));
        String approved = claims.findByReference(first).orElseThrow().getStatus() == ClaimStatus.APPROVED ? first : second;
        MockHttpSession approvedClaimant = approved.equals(first) ? claimantSession : strangerSession;
        var handoverStart = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> { handoverStart.await(); return mvc.perform(post("/api/claims/" + approved + "/handover").session(posterSession)).andReturn().getResponse().getStatus(); });
            var b = workers.submit(() -> { handoverStart.await(); return mvc.perform(post("/api/claims/" + approved + "/handover").session(approvedClaimant)).andReturn().getResponse().getStatus(); });
            handoverStart.countDown();
            assertEquals(200, a.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(200, b.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(ClaimStatus.ACCEPTED, claims.findByReference(approved).orElseThrow().getStatus());
        assertEquals(1, audit.findByClaimReferenceOrderByIdAsc(approved).stream().filter(event -> event.getAction().equals("HANDOVER_COMPLETED")).count());
    }
    private void reviewAndApprove(String ref) throws Exception {
        mvc.perform(post("/api/claims/" + ref + "/verify").session(posterSession)).andExpect(status().isOk());
        deskDecision(ref, true, true).andExpect(status().isOk());
        mvc.perform(post("/api/claims/" + ref + "/accept").session(posterSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private User user(Role role) {
        User user = new User();
        String suffix = UUID.randomUUID().toString();
        user.setUsername("test-" + suffix);
        user.setEmail(suffix + "@example.edu");
        user.setStudentId(suffix);
        user.setRole(role);
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        user.setSalt(passwords.generateSalt());
        user.setPasswordHash(passwords.hashPassword("test-password", user.getSalt()));
        return users.saveAndFlush(user);
    }

    private MockHttpSession session(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
        return session;
    }

    private MockMultipartFile itemPart() {
        String json = mapper.writeValueAsString(Map.of("kind", "FOUND", "category", "ELECTRONICS", "title", "A phone",
                "description", "Found near the campus desk", "location", "Library", "reporterName", "Fake name",
                "reporterEmail", "fake@example.edu", "securityQuestion", "What sticker is hidden?", "securityAnswer", "Hidden Sticker"));
        return new MockMultipartFile("item", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode postItem(boolean photos) throws Exception {
        var request = multipart("/api/items").file(itemPart()).session(posterSession);
        if (photos) request.file(new MockMultipartFile("photo", "public.png", "image/png", png()))
                .file(new MockMultipartFile("privatePhoto", "private.png", "image/png", png()));
        return json(mvc.perform(request).andExpect(status().isCreated()).andReturn());
    }

    private String claimBody(String answer) {
        return mapper.writeValueAsString(Map.of("claimantName", "Fake claimant", "claimantEmail", "fake@example.edu",
                "proof", "My identifying details are inside the case.", "securityAnswer", answer));
    }

    private JsonNode claim(String ref, MockHttpSession session, String answer) throws Exception {
        return json(mvc.perform(post("/api/items/" + ref + "/claims").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(claimBody(answer))).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}
