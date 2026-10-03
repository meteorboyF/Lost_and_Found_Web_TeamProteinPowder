package com.teamproteinpowder.lostfound.service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.teamproteinpowder.lostfound.domain.*;
import com.teamproteinpowder.lostfound.repo.UserRepository;

/** Generates prompts without sending item details or secrets to a third party. */
@Service
public class QuestionService {
    private final PasswordService passwords;
    private final UserRepository users;

    public QuestionService(PasswordService passwords, UserRepository users) {
        this.passwords = passwords;
        this.users = users;
    }

    public static String generate(Category category) {
        return switch (category) {
            case ELECTRONICS -> "What unique sticker, engraving, or identifying mark is on this device?";
            case ID_CARDS -> "What name or identifying detail is printed on the card?";
            case KEYS -> "What distinctive charm or marking is on the keyring?";
            case BAGS -> "What specific object is inside a pocket of the bag?";
            case CLOTHING -> "What name, initials, or distinctive detail is on the inside label?";
            case BOOKS -> "What name or handwritten note is inside the book or stationery?";
            case JEWELLERY -> "What engraving or distinctive detail is on the jewellery?";
            case OTHER -> "What hidden identifying detail does this item have?";
        };
    }

    public void configure(Item item, String question, String answer) {
        String normalized = normalize(answer);
        if (question == null || question.isBlank() || question.trim().length() > 300
                || normalized.length() < 3 || normalized.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a question and a secret answer of 3 to 200 characters");
        }
        item.setSecurityQuestion(question.trim());
        item.setSecurityAnswerSalt(passwords.generateSalt());
        item.setSecurityAnswerHash(passwords.hashPassword(normalized, item.getSecurityAnswerSalt()));
    }

    // Separate transaction retains failed attempts even when claim creation is rejected.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public void verify(Item item, User actor, String answer) {
        if (item.getSecurityAnswerHash() == null) return; // legacy: poster must review proof
        User user = users.lockForVerification(actor.getId()).orElseThrow();
        Instant now = Instant.now();
        if (user.getSecurityWindowStarted() == null
                || user.getSecurityWindowStarted().plus(15, ChronoUnit.MINUTES).isBefore(now)) {
            user.setSecurityWindowStarted(now);
            user.setSecurityFailures(0);
        }
        if (user.getSecurityFailures() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect answers. Try again in 15 minutes");
        }
        if (!passwords.verifyPassword(normalize(answer), item.getSecurityAnswerSalt(), item.getSecurityAnswerHash())) {
            user.setSecurityFailures(user.getSecurityFailures() + 1);
            users.saveAndFlush(user);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The security answer does not match. Chat remains locked");
        }
    }

    private static String normalize(String answer) {
        return Normalizer.normalize(answer == null ? "" : answer, Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
