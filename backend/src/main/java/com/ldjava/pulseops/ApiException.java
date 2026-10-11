package com.ldjava.pulseops;

import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
    final HttpStatus status;
    public ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "记录不存在"); }
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
