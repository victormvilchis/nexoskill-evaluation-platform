package com.nexoskill.evaluation.roles.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.roles.application.RoleManagementService;
import com.nexoskill.evaluation.roles.application.RoleManagementService.CloneCommand;
import com.nexoskill.evaluation.roles.application.RoleManagementService.PermissionCatalog;
import com.nexoskill.evaluation.roles.application.RoleManagementService.RoleDetail;
import com.nexoskill.evaluation.roles.application.RoleManagementService.RoleSummary;
import com.nexoskill.evaluation.roles.application.RoleManagementService.UpsertCommand;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.shared.interfaces.rest.PagedResponse;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/role-management")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class RoleManagementController {

    private static final Map<String, String> ALLOWED_SORTS = Map.of(
            "name", "name", "status", "status", "updatedAt", "updatedAt");

    private final RoleManagementService service;

    public RoleManagementController(RoleManagementService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<RoleSummary> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {
        var pageable = PaginationParameters.of(page, size, sort, direction, ALLOWED_SORTS,
                "name", Sort.Direction.ASC, "id");
        return PagedResponse.from(service.list(query, status, pageable), item -> item);
    }

    @GetMapping("/permissions")
    public PermissionCatalog permissions(
            @RequestParam(defaultValue = "ORGANIZATIONAL") String scope) {
        return service.permissionCatalog(scope);
    }

    @GetMapping("/{roleCode}")
    public RoleDetail get(@PathVariable String roleCode) {
        return service.get(roleCode);
    }

    @PostMapping
    public ResponseEntity<RoleDetail> create(@Valid @RequestBody UpsertRoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        RoleDetail created = service.create(body.toCommand(), actor(actor, request));
        return ResponseEntity.created(URI.create("/api/v1/admin/role-management/" + created.code())).body(created);
    }

    @PutMapping("/{roleCode}")
    public RoleDetail update(@PathVariable String roleCode, @Valid @RequestBody UpsertRoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.update(roleCode, body.toCommand(), actor(actor, request));
    }

    @PostMapping("/{roleCode}/clone")
    public ResponseEntity<RoleDetail> cloneRole(@PathVariable String roleCode,
            @Valid @RequestBody CloneRoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        RoleDetail created = service.cloneRole(roleCode, new CloneCommand(body.name(), body.description()),
                actor(actor, request));
        return ResponseEntity.created(URI.create("/api/v1/admin/role-management/" + created.code())).body(created);
    }

    @PostMapping("/{roleCode}/status")
    public RoleDetail changeStatus(@PathVariable String roleCode, @Valid @RequestBody StatusRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.changeStatus(roleCode, body.status(), actor(actor, request));
    }

    @DeleteMapping("/{roleCode}")
    public ResponseEntity<Void> delete(@PathVariable String roleCode,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        service.delete(roleCode, actor(actor, request));
        return ResponseEntity.noContent().build();
    }

    private RoleManagementService.Actor actor(AuthenticatedUser user, HttpServletRequest request) {
        return new RoleManagementService.Actor(user.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request));
    }

    public record UpsertRoleRequest(
            @NotBlank(message = "El nombre del rol es obligatorio.")
            @Size(max = 100, message = "El nombre del rol no puede superar 100 caracteres.") String name,
            @Size(max = 500, message = "La descripción no puede superar 500 caracteres.") String description,
            @NotNull(message = "La configuración de permisos es obligatoria.") Set<String> permissionCodes) {
        UpsertCommand toCommand() {
            return new UpsertCommand(name, description, permissionCodes);
        }
    }

    public record CloneRoleRequest(
            @NotBlank(message = "El nombre del nuevo rol es obligatorio.")
            @Size(max = 100, message = "El nombre del rol no puede superar 100 caracteres.") String name,
            @Size(max = 500, message = "La descripción no puede superar 500 caracteres.") String description) {
    }

    public record StatusRequest(@NotBlank String status) {
    }
}
