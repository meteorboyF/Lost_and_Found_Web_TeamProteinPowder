package com.teamproteinpowder.lostfound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.UserRepository;
import com.teamproteinpowder.lostfound.service.PasswordService;

import tools.jackson.databind.ObjectMapper;

/**
 * Admins sign in through the same login as everyone else, and roles and
 * approval are enforced on every request, so a change takes effect at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleManagementIntegrationTest {

    private static final String ORIGIN = "http://localhost";
    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;

    /** Its own address, so the shared login throttle can't interfere. */
    private final String clientAddress = "100.64.%d.%d".formatted(
            (int) (Math.random() * 250) + 1, (int) (Math.random() * 250) + 1);

    private User admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        admin = user(Role.ADMIN, ApprovalStatus.APPROVED);
        adminSession = login(admin);
    }

    private User user(Role role, ApprovalStatus status) {
        String tag = UUID.randomUUID().toString();
        User u = new User();
        u.setUsername("u-" + tag.substring(0, 8));
        u.setEmail(tag + "@example.edu");
        u.setStudentId(tag);
        u.setRole(role);
        u.setApprovalStatus(status);
        u.setSalt(passwords.generateSalt());
        u.setPasswordHash(passwords.hashPassword(PASSWORD, u.getSalt()));
        return users.saveAndFlush(u);
    }

    private int loginStatus(User u) throws Exception {
        return mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                        .with(r -> { r.setRemoteAddr(clientAddress); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("identifier", u.getUsername(), "password", PASSWORD))))
                .andReturn().getResponse().getStatus();
    }

    /** Signs in through the public login endpoint, exactly as the login page does. */
    private MockHttpSession login(User u) throws Exception {
        var result = mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                        .with(r -> { r.setRemoteAddr(clientAddress); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("identifier", u.getUsername(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private int adminArea(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/admin/overview").session(session)).andReturn().getResponse().getStatus();
    }

    private int setRole(MockHttpSession session, User target, String role) throws Exception {
        return mvc.perform(post("/api/admin/users/" + target.getId() + "/role").session(session).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("role", role))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void adminSignsInThroughTheNormalLoginAndReachesTheAdminArea() throws Exception {
        mvc.perform(get("/api/auth/me").session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        assertEquals(200, adminArea(adminSession));
    }

    @Test
    void promotingAStudentGrantsAdminAccessAndDemotingRemovesItAtOnce() throws Exception {
        User student = user(Role.USER, ApprovalStatus.APPROVED);
        MockHttpSession studentSession = login(student);
        assertEquals(401, adminArea(studentSession));

        assertEquals(200, setRole(adminSession, student, "ADMIN"));
        assertEquals(200, adminArea(studentSession), "promotion applies to the existing session");

        assertEquals(200, setRole(adminSession, student, "USER"));
        assertEquals(401, adminArea(studentSession), "demotion applies to the existing session");
    }

    @Test
    void onlyApprovedAccountsCanBePromoted() throws Exception {
        assertEquals(409, setRole(adminSession, user(Role.USER, ApprovalStatus.PENDING), "ADMIN"));
        assertEquals(409, setRole(adminSession, user(Role.USER, ApprovalStatus.REJECTED), "ADMIN"));
    }

    @Test
    void anAdminCannotDemoteThemselves() throws Exception {
        assertEquals(409, setRole(adminSession, admin, "USER"));
        assertEquals(200, adminArea(adminSession));
    }

    @Test
    void studentsCannotChangeRoles() throws Exception {
        User student = user(Role.USER, ApprovalStatus.APPROVED);
        assertEquals(401, setRole(login(student), student, "ADMIN"));
        assertEquals(Role.USER, users.findById(student.getId()).orElseThrow().getRole());
    }

    @Test
    void rejectingAnAdminActuallyTakesEffect() throws Exception {
        // Approval used to be skipped for admins, so a rejected admin kept full access.
        User other = user(Role.ADMIN, ApprovalStatus.APPROVED);
        MockHttpSession otherSession = login(other);
        assertEquals(200, adminArea(otherSession));

        mvc.perform(post("/api/admin/users/" + other.getId() + "/reject").session(adminSession).header("Origin", ORIGIN))
                .andExpect(status().isOk());

        assertEquals(401, adminArea(otherSession), "their open session loses access immediately");
        assertEquals(403, loginStatus(other), "and they cannot sign in again");
    }
}
