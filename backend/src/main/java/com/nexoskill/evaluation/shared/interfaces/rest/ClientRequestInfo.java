package com.nexoskill.evaluation.shared.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientRequestInfo {

    private ClientRequestInfo() {
    }

    public static String ipAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    public static String userAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), 1000);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
