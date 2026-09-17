package com.example.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> media(Exception e, HttpServletRequest r) {
    return error(415, "UNSUPPORTED_MEDIA_TYPE", "Use application/json for request bodies", r);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<?> missing(Exception e, HttpServletRequest r) {
    return error(404, "NOT_FOUND", "Resource not found", r);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> method(Exception e, HttpServletRequest r) {
    return error(405, "METHOD_NOT_ALLOWED", "Method not supported", r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e, HttpServletRequest r) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error(
            "operation=request_failure requestId={} type={}",
            r.getAttribute("requestId"),
            e.getClass().getSimpleName());
    return error(
        500, "INTERNAL_ERROR", "Unexpected server error; use the request ID when reporting", r);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> business(ApiException e, HttpServletRequest r) {
    return error(e.status, e.code, e.getMessage(), r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  ResponseEntity<?> invalid(Exception e, HttpServletRequest r) {
    return error(400, "VALIDATION_ERROR", "Check request fields, types and required headers", r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> constraint(Exception e, HttpServletRequest r) {
    return error(
        409, "DATA_CONFLICT", "Record conflicts with existing data or a business constraint", r);
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<?> database(Exception e, HttpServletRequest r) {
    return error(503, "DATABASE_UNAVAILABLE", "Database operation failed; retry later", r);
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<?> denied(Exception e, HttpServletRequest r) {
    return error(403, "FORBIDDEN", "Access denied", r);
  }

  private ResponseEntity<?> error(int status, String code, String message, HttpServletRequest r) {
    return ResponseEntity.status(status).body(ApiErrors.body(r, status, code, message));
  }
}
