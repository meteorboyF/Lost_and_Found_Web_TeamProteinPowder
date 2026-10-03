package com.teamproteinpowder.lostfound.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns exceptions into a consistent JSON shape.
 *
 * Validation failures come back as a field -> message map so the form can put
 * each message next to the input that caused it, instead of dumping one
 * combined string at the top of the page.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidationError(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            /* Keep the first message per field: showing three complaints about
               one input is noise, not help. */
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation failed");
        body.put("message", "Some fields need attention");
        body.put("fields", fields);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> onTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "status", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "error", "Upload too large",
                "message", "Photographs must be 5 MB or smaller",
                "timestamp", Instant.now().toString()));
    }

    /**
     * A unique constraint fired in the database.
     *
     * Every "does this already exist?" check in the services is check-then-
     * insert, so two simultaneous requests can both pass it; the database
     * constraint is what actually guarantees uniqueness. Without this handler
     * the request that lost that race got a bare 500. It now gets the same 409
     * a sequential duplicate would have produced.
     *
     * The raw driver message is never sent to the client — it can contain SQL
     * and the conflicting value. Only the constraint name is inspected, to pick
     * a human message; matching is case-insensitive because MySQL and H2
     * report index names in different case.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> onIntegrityViolation(DataIntegrityViolationException ex) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(java.util.Locale.ROOT);

        String message;
        if (cause.contains("idx_user_email")) {
            message = "An account with this student email already exists";
        } else if (cause.contains("idx_user_username")) {
            message = "Username is already taken";
        } else if (cause.contains("uk_user_student_id")) {
            message = "A student account with this Student ID already exists";
        } else {
            message = "This conflicts with an existing record";
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", "Conflict",
                "message", message,
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> onStatusException(ResponseStatusException ex) {
        ProblemDetail detail = ex.getBody();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", detail.getTitle() == null ? "Request failed" : detail.getTitle(),
                "message", ex.getReason() == null ? "Request failed" : ex.getReason(),
                "timestamp", Instant.now().toString()));
    }
}
