package com.nexoskill.evaluation.dashboard.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.dashboard.application.model.WelcomeDashboard;
import com.nexoskill.evaluation.dashboard.application.service.GetWelcomeDashboardService;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

	private final GetWelcomeDashboardService service;

	public DashboardController(GetWelcomeDashboardService service) {
		this.service = service;
	}

	@GetMapping("/welcome")
	@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
	public ResponseEntity<WelcomeDashboard> welcome(@AuthenticationPrincipal AuthenticatedUser principal,
			HttpServletRequest request) {
		return ResponseEntity
				.ok(service.get(principal, ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
	}
}
