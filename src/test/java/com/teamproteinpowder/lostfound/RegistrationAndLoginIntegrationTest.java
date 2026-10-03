package com.teamproteinpowder.lostfound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.teamproteinpowder.lostfound.repo.UserRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Registration and login behaviour that only shows up under concurrency or
 * under attack. Each test uses unique identifiers so they cannot interfere,
 * and every request sends a same-origin header like a real browser would.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationAndLoginIntegrationTest {

    private static final String ORIGIN = "http://localhost";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;

    private String body(String username, String email, String studentId) {
        return mapper.writeValueAsString(Map.of(
                "username", username,
                "email", email,
                "studentId", studentId,
                "password", "Correct-Horse-42"));
    }

    /** Messages returned by the 409s of the most recent race. */
    private final java.util.Set<String> conflictMessages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Fire n registrations at the same instant; return each HTTP status. */
    private List<Integer> raceRegistrations(int n, IntFunction<String> bodyFor) throws Exception {
        conflictMessages.clear();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                String json = bodyFor.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    var response = mvc.perform(post("/api/auth/register")
                                    .header("Origin", ORIGIN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn().getResponse();
                    if (response.getStatus() == 409) {
                        conflictMessages.add(mapper.readTree(response.getContentAsString()).path("message").asString());
                    }
                    return response.getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(20, TimeUnit.SECONDS));
            }
            return statuses;
        }
    }

    private static long count(List<Integer> statuses, int code) {
        return statuses.stream().filter(s -> s == code).count();
    }

    // ------------------------------------------------------------------
    // Uniqueness under concurrency
    // ------------------------------------------------------------------

    @Test
    void concurrentSignupsWithOneStudentIdCreateExactlyOneAccount() throws Exception {
        // Before the fix all eight succeeded, leaving eight accounts on one ID.
        String sid = "RACE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String tag = UUID.randomUUID().toString().substring(0, 6);

        List<Integer> statuses = raceRegistrations(8,
                i -> body("sid-" + tag + "-" + i, "sid-" + tag + "-" + i + "@campus.edu", sid));

        assertEquals(1, count(statuses, 201), "exactly one signup may own the student ID: " + statuses);
        assertEquals(7, count(statuses, 409), "the rest must be clean conflicts: " + statuses);
        assertEquals(java.util.Set.of("A student account with this Student ID already exists"), conflictMessages);
        assertEquals(1, users.findAll().stream().filter(u -> sid.equals(u.getStudentId())).count());
    }

    @Test
    void concurrentSignupsWithOneEmailReturnConflictNotServerError() throws Exception {
        // Before the fix the database rejected the duplicates correctly, but
        // every request that lost the race got a bare 500.
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String email = "shared-" + tag + "@campus.edu";

        List<Integer> statuses = raceRegistrations(8,
                i -> body("em-" + tag + "-" + i, email, "EM-" + tag + "-" + i));

        assertEquals(0, count(statuses, 500), "a lost race must never surface as a 500: " + statuses);
        assertEquals(1, count(statuses, 201), statuses.toString());
        assertEquals(7, count(statuses, 409), statuses.toString());
        // The user must be told which field clashed, not given a generic error.
        assertEquals(java.util.Set.of("An account with this student email already exists"), conflictMessages);
    }

    @Test
    void studentIdsDifferingOnlyByCaseAreTheSameId() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        mvc.perform(post("/api/auth/register").header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("lower-" + tag, "lower-" + tag + "@campus.edu", "case-" + tag)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value("CASE-" + tag));

        mvc.perform(post("/api/auth/register").header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("upper-" + tag, "upper-" + tag + "@campus.edu", "CASE-" + tag)))
                .andExpect(status().isConflict());
    }

    @Test
    void conflictMessagesNameTheFieldAndNeverLeakSql() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/auth/register").header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("first-" + tag, "dup-" + tag + "@campus.edu", "DUP-" + tag)))
                .andExpect(status().isCreated());

        String response = mvc.perform(post("/api/auth/register").header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second-" + tag, "dup-" + tag + "@campus.edu", "OTHER-" + tag)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        String lower = response.toLowerCase();
        assertEquals(false, lower.contains("insert into"), response);
        assertEquals(false, lower.contains("duplicate entry"), response);
        assertEquals(false, lower.contains("constraint"), response);
    }

    // ------------------------------------------------------------------
    // Login brute-force protection, end to end through the controller
    // ------------------------------------------------------------------

    /**
     * The throttle is a singleton and Spring shares one context across test
     * classes, so every MockMvc request would otherwise come from 127.0.0.1 and
     * draw on one shared per-address budget. A distinct address per test keeps
     * them independent, and models separate clients more honestly anyway.
     */
    private final String clientAddress = "10.%d.%d.%d".formatted(
            (int) (Math.random() * 250) + 1, (int) (Math.random() * 250) + 1, (int) (Math.random() * 250) + 1);

    private int login(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                        .with(request -> { request.setRemoteAddr(clientAddress); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void loginLocksAfterFiveFailuresAndRefusesEvenTheCorrectPassword() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String username = "victim-" + tag;
        mvc.perform(post("/api/auth/register").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(username, username + "@campus.edu", "V-" + tag)))
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(username, "wrong-" + i), "attempt " + (i + 1));
        }
        assertEquals(429, login(username, "wrong-again"));
        // Locked means locked: guessing right now must not let anyone in.
        assertEquals(429, login(username, "Correct-Horse-42"));
    }

    @Test
    void unknownUsernamesAreThrottledExactlyLikeRealOnes() throws Exception {
        // If only real accounts could lock, a 429 would confirm an account exists.
        String ghost = "ghost-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(ghost, "x"));
        }
        assertEquals(429, login(ghost, "x"));
    }
}
