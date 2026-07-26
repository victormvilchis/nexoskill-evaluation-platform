package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.students.application.StudentAuthenticationService;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import com.nexoskill.evaluation.students.infrastructure.security.StudentSessionCookieSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student-auth")
public class StudentAuthenticationController {
    private final StudentAuthenticationService service;
    private final StudentSessionCookieSupport cookieSupport;
    private final Clock clock;

    public StudentAuthenticationController(StudentAuthenticationService service,
            StudentSessionCookieSupport cookieSupport, Clock clock) {
        this.service = service;
        this.cookieSupport = cookieSupport;
        this.clock = clock;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        StudentAuthenticationService.LoginResult result = service.login(
                new StudentAuthenticationService.LoginCommand(body.organizationCode(), body.email(), body.password(),
                        ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieSupport.create(result.rawToken(), positiveDuration(result.expiresAt())).toString());
        return ResponseEntity.ok(new LoginResponse(StudentIdentityResponse.from(result.student())));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('STUDENT_PORTAL')")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedStudent principal,
            HttpServletRequest request, HttpServletResponse response) {
        service.logout(cookieSupport.readToken(request), principal, ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAuthority('STUDENT_PASSWORD_CHANGE')")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedStudent principal,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        service.changePassword(principal, body.currentPassword(), body.newPassword(), body.confirmPassword(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString());
        return ResponseEntity.noContent().build();
    }

    private Duration positiveDuration(java.time.Instant expiresAt) {
        Duration value = Duration.between(clock.instant(), expiresAt);
        return value.isNegative() || value.isZero() ? Duration.ofSeconds(1) : value;
    }

    public record LoginRequest(
            @NotBlank @Size(max = 80) String organizationCode,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {}
    public record LoginResponse(StudentIdentityResponse student) {}
    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword,
            @NotBlank @Size(min = 10, max = 128) String confirmPassword) {}
}
