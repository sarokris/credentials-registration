package com.credentials.dto;


import java.util.Map;

public record ErrorResponse (String errorCode, String message, Map<String, String> details) {
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
