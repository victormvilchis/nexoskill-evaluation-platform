package com.nexoskill.evaluation.dashboard.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Configuration;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Filter;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Overview;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.SaveConfigurationCommand;
import com.nexoskill.evaluation.dashboard.application.model.WelcomeDashboard;
import com.nexoskill.evaluation.dashboard.application.service.ExecutiveDashboardService;
import com.nexoskill.evaluation.dashboard.application.service.GetWelcomeDashboardService;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final GetWelcomeDashboardService welcomeService;
    private final ExecutiveDashboardService executiveService;
    private final TenantContextResolver tenantContextResolver;

    public DashboardController(GetWelcomeDashboardService welcomeService, ExecutiveDashboardService executiveService,
            TenantContextResolver tenantContextResolver) {
        this.welcomeService = welcomeService;
        this.executiveService = executiveService;
        this.tenantContextResolver = tenantContextResolver;
    }

    @GetMapping("/welcome")
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('DASHBOARD_VIEW')")
    public ResponseEntity<WelcomeDashboard> welcome(@AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(welcomeService.get(principal, ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('DASHBOARD_VIEW')")
    public ResponseEntity<Overview> overview(@AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request,
            @RequestParam(required = false) String organizationPublicId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String collaboratorStatus,
            @RequestParam(required = false) String certificationType,
            @RequestParam(required = false) String certificationState) {
        Filter filter = new Filter(organizationPublicId, role, technology, collaboratorStatus,
                certificationType, certificationState);
        return ResponseEntity.ok(executiveService.overview(tenantContextResolver.resolve(request), principal, filter));
    }

    @GetMapping("/configuration")
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('DASHBOARD_VIEW')")
    public ResponseEntity<Configuration> configuration(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(executiveService.configuration(principal));
    }

    @PutMapping("/configuration")
    @PreAuthorize("hasRole('ADMINISTRATOR') or (hasAuthority('DASHBOARD_VIEW') and hasAuthority('DASHBOARD_PERSONALIZE'))")
    public ResponseEntity<Configuration> saveConfiguration(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody SaveConfigurationCommand command) {
        return ResponseEntity.ok(executiveService.saveConfiguration(principal, command));
    }
}
