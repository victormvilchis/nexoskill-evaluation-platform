package com.nexoskill.evaluation.users.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.CreateUserCommand;
import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.application.service.CreateUserService;
import com.nexoskill.evaluation.users.application.service.ListRolesService;
import com.nexoskill.evaluation.users.application.service.SearchUsersService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final SearchUsersService searchUsersService;
    private final CreateUserService createUserService;
    private final ListRolesService listRolesService;

    public AdminUserController(
            SearchUsersService searchUsersService,
            CreateUserService createUserService,
            ListRolesService listRolesService) {
        this.searchUsersService = searchUsersService;
        this.createUserService = createUserService;
        this.listRolesService = listRolesService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public AdminUserPage search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchUsersService.search(query, status, page, size);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<AdminUserSummary> create(
            @Valid @RequestBody CreateUserRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {

        AdminUserSummary created = createUserService.create(
                new CreateUserCommand(
                        body.email(),
                        body.firstName(),
                        body.lastName(),
                        body.displayName(),
                        body.roleCode(),
                        body.temporaryPassword(),
                        body.startsAt(),
                        body.expiresAt(),
                        actor.internalId(),
                        ClientRequestInfo.ipAddress(request),
                        ClientRequestInfo.userAgent(request)
                )
        );

        return ResponseEntity.created(
                URI.create("/api/v1/admin/users/" + created.publicId())
        ).body(created);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public List<RoleOption> roles() {
        return listRolesService.list();
    }
}
