package com.nexoskill.evaluation.authentication.interfaces.rest;

import com.nexoskill.evaluation.authentication.application.model.ChangePasswordCommand;
import com.nexoskill.evaluation.authentication.application.model.LoginCommand;
import com.nexoskill.evaluation.authentication.application.model.LoginResult;
import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.application.service.ChangeOwnPasswordService;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
	private final ChangeOwnPasswordService changeOwnPasswordService;
	private final SessionCookieSupport cookieSupport;
	private final TokenHasher tokenHasher;
	private final Clock clock;

	public AuthenticationController(LoginService loginService, LogoutService logoutService,
			ChangeOwnPasswordService changeOwnPasswordService, SessionCookieSupport cookieSupport,
			TokenHasher tokenHasher, Clock clock) {
		this.loginService = loginService;
		this.logoutService = logoutService;
		this.changeOwnPasswordService = changeOwnPasswordService;
		this.cookieSupport = cookieSupport;
		this.tokenHasher = tokenHasher;
		this.clock = clock;
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {

		LoginResult result = loginService.login(new LoginCommand(body.email(), body.password(),
				ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));

		response.addHeader(HttpHeaders.SET_COOKIE,
				cookieSupport
						.create(result.rawSessionToken(), positiveDurationBetween(clock.instant(), result.expiresAt()))
						.toString());

		return ResponseEntity.ok(new LoginResponse(result.user(), result.user().passwordChangeRequired()));
	}

	@PostMapping("/change-password")
	@PreAuthorize("hasAuthority('PASSWORD_CHANGE')")
	public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request, HttpServletResponse response) {

		String rawSessionToken = cookieSupport.readToken(request);
		if (principal == null || rawSessionToken == null || rawSessionToken.isBlank()) {
			throw com.nexoskill.evaluation.authentication.domain.AuthenticationException.unauthorized();
		}
		changeOwnPasswordService.change(new ChangePasswordCommand(principal.internalId(), body.currentPassword(),
				body.newPassword(), body.confirmPassword(), tokenHasher.hash(rawSessionToken),
				ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
		response.addHeader(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request,
			HttpServletResponse response) {

		logoutService.logout(cookieSupport.readToken(request), principal == null ? null : principal.internalId(),
				ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request));

		response.addHeader(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString());

		return ResponseEntity.noContent().build();
	}

	private Duration positiveDurationBetween(java.time.Instant startsAt, java.time.Instant expiresAt) {
		Duration duration = Duration.between(startsAt, expiresAt);
		return duration.isNegative() || duration.isZero() ? Duration.ofSeconds(1) : duration;
	}
}
