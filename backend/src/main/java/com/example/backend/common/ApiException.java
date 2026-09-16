package com.example.backend.common;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public static ApiException conflict(String code, String message) { return new ApiException(409, code, message); }
    public static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "You cannot access this resource"); }
}
