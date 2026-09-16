package com.example.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> business(ApiException e, HttpServletRequest r) { return error(e.status, e.code, e.getMessage(), r); }
    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<?> invalid(Exception e, HttpServletRequest r) { return error(400, "VALIDATION_ERROR", "Check request fields, types and required headers", r); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> constraint(Exception e, HttpServletRequest r) { return error(409, "DATA_CONFLICT", "Record conflicts with existing data or a business constraint", r); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(Exception e, HttpServletRequest r) { return error(503, "DATABASE_UNAVAILABLE", "Database operation failed; retry later", r); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<?> denied(Exception e, HttpServletRequest r) { return error(403, "FORBIDDEN", "Access denied", r); }
    private ResponseEntity<?> error(int status, String code, String message, HttpServletRequest r) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "status", status, "code", code, "message", message, "path", r.getRequestURI()));
    }
}
