package com.nexoskill.evaluation.users.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.users.application.model.UpdateOwnProfileCommand;
import com.nexoskill.evaluation.users.application.service.GetOwnProfileService;
import com.nexoskill.evaluation.users.application.service.UpdateOwnProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final GetOwnProfileService getOwnProfileService;
	private final UpdateOwnProfileService updateOwnProfileService;

	public UserController(GetOwnProfileService getOwnProfileService, UpdateOwnProfileService updateOwnProfileService) {
		this.getOwnProfileService = getOwnProfileService;
		this.updateOwnProfileService = updateOwnProfileService;
	}

	@GetMapping("/me")
	public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
		return ResponseEntity.ok(new CurrentUserResponse(principal.toCurrentUser()));
	}

	@GetMapping("/me/profile")
	@PreAuthorize("hasAuthority('PROFILE_VIEW')")
	public ResponseEntity<OwnProfileResponse> profile(@AuthenticationPrincipal AuthenticatedUser principal) {
		return ResponseEntity.ok(new OwnProfileResponse(getOwnProfileService.get(principal.publicId())));
	}

	@PutMapping("/me/profile")
	@PreAuthorize("hasAuthority('PROFILE_UPDATE')")
	public ResponseEntity<OwnProfileResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody UpdateOwnProfileRequest body, HttpServletRequest request) {
		return ResponseEntity.ok(new OwnProfileResponse(updateOwnProfileService.update(new UpdateOwnProfileCommand(
				principal.internalId(), principal.publicId(), body.firstName(), body.lastName(), body.displayName(),
				ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)))));
	}
}
