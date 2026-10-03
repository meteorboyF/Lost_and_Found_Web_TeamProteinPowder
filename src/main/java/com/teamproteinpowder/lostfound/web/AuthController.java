package com.teamproteinpowder.lostfound.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;
import com.teamproteinpowder.lostfound.repo.UserRepository;
import com.teamproteinpowder.lostfound.service.LoginThrottle;
import com.teamproteinpowder.lostfound.service.PasswordService;
import com.teamproteinpowder.lostfound.web.dto.LoginRequest;
import com.teamproteinpowder.lostfound.web.dto.RegisterRequest;
import com.teamproteinpowder.lostfound.web.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_USER_ID = "AUTH_USER_ID";
    public static final String SESSION_USER_EMAIL = "AUTH_USER_EMAIL";
    public static final String SESSION_USER_ROLE = "AUTH_USER_ROLE";
    public static final String SESSION_USER_NAME = "AUTH_USER_NAME";

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final LoginThrottle throttle;

    /**
     * A real salted hash that no password matches, verified whenever the
     * account does not exist. PBKDF2 at 65,536 iterations takes ~200 ms; without
     * this, an unknown username answered in ~5 ms and response time alone
     * revealed which usernames and emails are registered.
     */
    private final String dummySalt;
    private final String dummyHash;

    public AuthController(UserRepository userRepository, PasswordService passwordService,
                          LoginThrottle throttle) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.throttle = throttle;
        this.dummySalt = passwordService.generateSalt();
        this.dummyHash = passwordService.hashPassword(java.util.UUID.randomUUID().toString(), dummySalt);
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();
        String username = request.getUsername().trim();
        String studentId = request.getStudentId().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this student email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByStudentIdIgnoreCase(studentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A student account with this Student ID already exists");
        }

        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(request.getPassword(), salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setStudentEmail(email);
        user.setStudentId(studentId);
        user.setSalt(salt);
        user.setPasswordHash(hash);
        user.setRole(Role.USER);
        user.setApprovalStatus(ApprovalStatus.PENDING);

        User saved = userRepository.save(user);

        // Account is pending verification by admin, do NOT bind session yet
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(saved));
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        String identifier = request.getIdentifier().trim();
        String ip = httpRequest.getRemoteAddr();

        /* Before any lookup or hashing: a locked client gets nothing, and
           cannot use this endpoint to burn CPU on PBKDF2 either. */
        throttle.checkAllowed(identifier, ip);

        Optional<User> userOpt = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier.toLowerCase())
                : userRepository.findByUsernameIgnoreCase(identifier);

        /* Always run exactly one PBKDF2 verification, real or dummy, so a
           missing account and a wrong password take the same time and give
           the same answer. */
        boolean passwordOk;
        if (userOpt.isPresent()) {
            User candidate = userOpt.get();
            passwordOk = passwordService.verifyPassword(request.getPassword(), candidate.getSalt(), candidate.getPasswordHash());
        } else {
            // Result deliberately ignored: this call exists only to spend the same time.
            passwordService.verifyPassword(request.getPassword(), dummySalt, dummyHash);
            passwordOk = false;
        }

        if (!passwordOk) {
            throttle.recordFailure(identifier, ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email/username or password");
        }

        User user = userOpt.get();
        throttle.recordSuccess(identifier);

        // Verification check: Non-admin users must be approved by admin
        if (user.getRole() != Role.ADMIN) {
            if (user.getApprovalStatus() == ApprovalStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your account is pending administrator approval. Please wait for an administrator to verify your student credentials (Student ID: "
                                + user.getStudentId() + ").");
            } else if (user.getApprovalStatus() == ApprovalStatus.REJECTED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your registration was rejected by an administrator. Please contact campus support.");
            }
        }

        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        bindSession(session, user);

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<UserResponse> me(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER_ID) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!user.isApproved()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account approval required");

        return ResponseEntity.ok(UserResponse.from(user));
    }

    private void bindSession(HttpSession session, User user) {
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setAttribute(SESSION_USER_EMAIL, user.getEmail().toLowerCase());
        session.setAttribute(SESSION_USER_ROLE, user.getRole().name());
        session.setAttribute(SESSION_USER_NAME, user.getUsername());
    }
}
