package com.nexoskill.evaluation.authentication.interfaces.rest;

import com.nexoskill.evaluation.authentication.application.model.LoginCommand;
import com.nexoskill.evaluation.authentication.application.model.LoginResult;
import com.nexoskill.evaluation.authentication.application.service.LoginService;
import com.nexoskill.evaluation.authentication.application.service.LogoutService;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.authentication.infrastructure.security.SessionCookieSupport;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final LoginService loginService;
    private final LogoutService logoutService;
    private final SessionCookieSupport cookieSupport;
    private final Clock clock;

    public AuthenticationController(
            LoginService loginService,
            LogoutService logoutService,
            SessionCookieSupport cookieSupport,
            Clock clock) {
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.cookieSupport = cookieSupport;
        this.clock = clock;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResult result = loginService.login(new LoginCommand(
                body.email(),
                body.password(),
                ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)
        ));

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieSupport.create(
                        result.rawSessionToken(),
                        positiveDurationBetween(clock.instant(), result.expiresAt())
                ).toString()
        );

        return ResponseEntity.ok(new LoginResponse(result.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request,
            HttpServletResponse response) {

        logoutService.logout(
                cookieSupport.readToken(request),
                principal.internalId(),
                ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieSupport.clear().toString()
        );

        return ResponseEntity.noContent().build();
    }

    private Duration positiveDurationBetween(
            java.time.Instant startsAt,
            java.time.Instant expiresAt) {
        Duration duration = Duration.between(startsAt, expiresAt);
        return duration.isNegative() || duration.isZero()
                ? Duration.ofSeconds(1)
                : duration;
    }
}
