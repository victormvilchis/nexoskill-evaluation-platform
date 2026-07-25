package com.nexoskill.evaluation.users.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.users.application.model.*;
import com.nexoskill.evaluation.users.application.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
    private final UpdateUserProfileService updateUserProfileService;
    private final UpdateUserAccessService updateUserAccessService;
    private final UpdateUserRoleService updateUserRoleService;
    private final ActivateUserService activateUserService;
    private final SuspendUserService suspendUserService;
    private final DeleteUserService deleteUserService;
    private final RestoreUserService restoreUserService;
    private final ResetUserPasswordService resetUserPasswordService;

    public AdminUserController(SearchUsersService searchUsersService, CreateUserService createUserService,
            ListRolesService listRolesService, GetAdminUserService getAdminUserService,
            UpdateUserProfileService updateUserProfileService, UpdateUserAccessService updateUserAccessService,
            UpdateUserRoleService updateUserRoleService, ActivateUserService activateUserService,
            SuspendUserService suspendUserService, DeleteUserService deleteUserService,
            RestoreUserService restoreUserService, ResetUserPasswordService resetUserPasswordService) {
        this.searchUsersService = searchUsersService; this.createUserService = createUserService;
        this.listRolesService = listRolesService; this.getAdminUserService = getAdminUserService;
        this.updateUserProfileService = updateUserProfileService; this.updateUserAccessService = updateUserAccessService;
        this.updateUserRoleService = updateUserRoleService; this.activateUserService = activateUserService;
        this.suspendUserService = suspendUserService; this.deleteUserService = deleteUserService;
        this.restoreUserService = restoreUserService; this.resetUserPasswordService = resetUserPasswordService;
    }

    @GetMapping("/users") @PreAuthorize("hasAuthority('USER_VIEW')")
    public AdminUserPage search(@RequestParam(required=false) String query,
            @RequestParam(required=false) String status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return searchUsersService.search(query, status, page, size);
    }
    @GetMapping("/users/{publicId}") @PreAuthorize("hasAuthority('USER_VIEW')")
    public AdminUserSummary get(@PathVariable String publicId) { return getAdminUserService.get(publicId); }

    @PostMapping("/users") @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<AdminUserSummary> create(@Valid @RequestBody CreateUserRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        AdminUserSummary created = createUserService.create(new CreateUserCommand(body.email(), body.firstName(),
                body.lastName(), body.displayName(), body.roleCode(), body.temporaryPassword(), body.startsAt(),
                body.expiresAt(), actor.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.publicId())).body(created);
    }

    @PutMapping("/users/{publicId}") @PreAuthorize("hasAuthority('USER_UPDATE')")
    public AdminUserSummary updateProfile(@PathVariable String publicId,
            @Valid @RequestBody UpdateUserProfileRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return updateUserProfileService.update(new UpdateUserProfileCommand(publicId, body.email(), body.firstName(),
                body.lastName(), body.displayName(), actor.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)));
    }

    @PutMapping("/users/{publicId}/access") @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public AdminUserSummary updateAccess(@PathVariable String publicId,
            @Valid @RequestBody UpdateUserAccessRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return updateUserAccessService.update(new UpdateUserAccessCommand(publicId, body.startsAt(), body.expiresAt(),
                actor.internalId(), ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @PutMapping("/users/{publicId}/role") @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public AdminUserSummary updateRole(@PathVariable String publicId,
            @Valid @RequestBody UpdateUserRoleRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return updateUserRoleService.update(new UpdateUserRoleCommand(publicId, body.roleCode(), actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @PostMapping("/users/{publicId}/activate") @PreAuthorize("hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary activate(@PathVariable String publicId, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) { return activateUserService.activate(statusCommand(publicId, actor, request)); }

    @PostMapping("/users/{publicId}/suspend") @PreAuthorize("hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary suspend(@PathVariable String publicId, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) { return suspendUserService.suspend(statusCommand(publicId, actor, request)); }

    @PostMapping("/users/{publicId}/delete") @PreAuthorize("hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary delete(@PathVariable String publicId, @RequestBody(required=false) DeleteUserRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return deleteUserService.delete(new DeleteUserCommand(publicId, body == null ? null : body.reason(),
                actor.internalId(), ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @PostMapping("/users/{publicId}/restore") @PreAuthorize("hasAuthority('USER_STATUS_CHANGE')")
    public AdminUserSummary restore(@PathVariable String publicId, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) { return restoreUserService.restore(statusCommand(publicId, actor, request)); }

    @PostMapping("/users/{publicId}/reset-password") @PreAuthorize("hasAuthority('USER_PASSWORD_RESET')")
    public AdminUserSummary resetPassword(@PathVariable String publicId,
            @Valid @RequestBody ResetUserPasswordRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return resetUserPasswordService.reset(new ResetUserPasswordCommand(publicId, body.temporaryPassword(),
                actor.internalId(), ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
    }

    @GetMapping("/roles") @PreAuthorize("hasAnyAuthority('USER_CREATE','USER_ROLE_ASSIGN')")
    public List<RoleOption> roles() { return listRolesService.list(); }

    private UserStatusCommand statusCommand(String publicId, AuthenticatedUser actor, HttpServletRequest request) {
        return new UserStatusCommand(publicId, actor.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request));
    }
}
