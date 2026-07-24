package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieSupport {

    private final AppProperties properties;

    public SessionCookieSupport(AppProperties properties) {
        this.properties = properties;
    }

    public String readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getSecurity().getCookieName()
                        .equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public ResponseCookie create(String rawToken, Duration maxAge) {
        return ResponseCookie.from(
                        properties.getSecurity().getCookieName(),
                        rawToken
                )
                .httpOnly(true)
                .secure(properties.getSecurity().isCookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return create("", Duration.ZERO);
    }
}
