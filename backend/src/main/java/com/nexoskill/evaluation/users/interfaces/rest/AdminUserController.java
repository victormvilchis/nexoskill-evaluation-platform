package com.nexoskill.evaluation.users.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import com.nexoskill.evaluation.users.application.model.*;
import com.nexoskill.evaluation.users.application.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {
    private final SearchUsersService searchUsersService;
    private final CreateUserService createUserService;
    private final ListRolesService listRolesService;
    private final GetAdminUserService getAdminUserService;
    private final UpdateInternalUserService updateInternalUserService;
    private final ActivateUserService activateUserService;
    private final DeactivateUserService deactivateUserService;
    private final SuspendUserService suspendUserService;
    private final DeleteUserService deleteUserService;
    private final RestoreUserService restoreUserService;
    private final ResetUserPasswordService resetUserPasswordService;
    private final InternalUserStatusHistoryService statusHistoryService;
    private final InternalUserSessionService sessionService;

    public AdminUserController(SearchUsersService searchUsersService, CreateUserService createUserService,
            ListRolesService listRolesService, GetAdminUserService getAdminUserService,
            UpdateInternalUserService updateInternalUserService, ActivateUserService activateUserService,
            DeactivateUserService deactivateUserService, SuspendUserService suspendUserService,
            DeleteUserService deleteUserService, RestoreUserService restoreUserService,
            ResetUserPasswordService resetUserPasswordService,
            InternalUserStatusHistoryService statusHistoryService, InternalUserSessionService sessionService) {
        this.searchUsersService = searchUsersService;
        this.createUserService = createUserService;
        this.listRolesService = listRolesService;
        this.getAdminUserService = getAdminUserService;
        this.updateInternalUserService = updateInternalUserService;
        this.activateUserService = activateUserService;
        this.deactivateUserService = deactivateUserService;
        this.suspendUserService = suspendUserService;
        this.deleteUserService = deleteUserService;
        this.restoreUserService = restoreUserService;
        this.resetUserPasswordService = resetUserPasswordService;
        this.statusHistoryService = statusHistoryService;
        this.sessionService = sessionService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public AdminUserPage search(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationParameters.validate(page, size);
        return searchUsersService.search(query, status, page, size);
    }

    @GetMapping("/users/{publicId}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public AdminUserSummary get(@PathVariable String publicId) {
        return getAdminUserService.get(publicId);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_CREATE')")
    public ResponseEntity<CreateUserResponse> create(@Valid @RequestBody CreateUserRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        CreateUserResult result = createUserService.create(new CreateUserCommand(body.email(), body.firstName(),
                body.lastName(), body.displayName(), body.roleCode(), body.organizationPublicId(),
                body.startsAt(), body.expiresAt(), actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + result.user().publicId()))
                .body(new CreateUserResponse(result.user(), result.temporaryPassword()));
    }

    @PutMapping("/users/{publicId}")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_UPDATE')")
    public AdminUserSummary update(@PathVariable String publicId,
            @Valid @RequestBody UpdateInternalUserRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return updateInternalUserService.update(new UpdateInternalUserCommand(publicId, body.email(),
                body.firstName(), body.lastName(), body.displayName(), body.roleCode(),
                body.organizationPublicId(), body.startsAt(), body.expiresAt(), actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @PostMapping("/users/{publicId}/activate")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary activate(@PathVariable String publicId,
            @RequestBody(required = false) UserStatusChangeRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return activateUserService.activate(statusCommand(publicId, body, actor, request));
    }

    @PostMapping("/users/{publicId}/deactivate")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary deactivate(@PathVariable String publicId,
            @RequestBody(required = false) UserStatusChangeRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return deactivateUserService.deactivate(statusCommand(publicId, body, actor, request));
    }

    @PostMapping("/users/{publicId}/suspend")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary suspend(@PathVariable String publicId,
            @Valid @RequestBody UserStatusChangeRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return suspendUserService.suspend(statusCommand(publicId, body, actor, request));
    }

    @DeleteMapping("/users/{publicId}")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary delete(@PathVariable String publicId,
            @Valid @RequestBody UserStatusChangeRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return deleteUserService.delete(new DeleteUserCommand(publicId, body.reason(), actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @PostMapping("/users/{publicId}/restore")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary restore(@PathVariable String publicId,
            @RequestBody(required = false) UserStatusChangeRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return restoreUserService.restore(statusCommand(publicId, body, actor, request));
    }

    @PostMapping("/users/{publicId}/reset-password")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_PASSWORD_RESET')")
    public TemporaryPasswordResponse resetPassword(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        TemporaryPasswordResult result = resetUserPasswordService.reset(new ResetUserPasswordCommand(publicId,
                actor.internalId(), ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
        return new TemporaryPasswordResponse(result.user(), result.temporaryPassword());
    }

    @GetMapping("/users/{publicId}/status-history")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_VIEW')")
    public List<InternalUserStatusHistory> history(@PathVariable String publicId) {
        return statusHistoryService.list(publicId);
    }

    @GetMapping("/users/{publicId}/sessions")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_VIEW')")
    public List<InternalUserSessionSummary> sessions(@PathVariable String publicId) {
        return sessionService.list(publicId);
    }

    @PostMapping("/users/{publicId}/revoke-sessions")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('USER_STATUS_CHANGE')")
    public Map<String, Integer> revokeSessions(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return Map.of("revokedSessions", sessionService.revoke(publicId, actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAnyAuthority('USER_CREATE','USER_ROLE_ASSIGN','USER_UPDATE')")
    public List<RoleOption> roles() {
        return listRolesService.list();
    }

    private UserStatusCommand statusCommand(String publicId, UserStatusChangeRequest body, AuthenticatedUser actor,
            HttpServletRequest request) {
        return new UserStatusCommand(publicId, body == null ? null : body.reason(), actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request));
    }
}
