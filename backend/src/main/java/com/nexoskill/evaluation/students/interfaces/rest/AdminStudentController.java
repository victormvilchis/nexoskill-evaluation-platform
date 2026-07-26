package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.students.application.StudentService;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/students")
public class AdminStudentController {
    private final StudentService service;
    private final TenantContextResolver tenantResolver;

    public AdminStudentController(StudentService service, TenantContextResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentService.PageResult search(HttpServletRequest request,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(tenant(request), query, parseStatus(status), includeDeleted, page, size);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentService.StudentDetail get(@PathVariable String publicId, HttpServletRequest request) {
        return service.get(tenant(request), publicId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public ResponseEntity<StudentService.StudentDetail> create(@Valid @RequestBody CreateRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        StudentService.StudentDetail created = service.create(tenant(request),
                new StudentService.CreateCommand(body.studentCode(), body.email(), body.firstName(), body.lastName(),
                        body.displayName(), body.temporaryPassword(), body.status(), body.validFrom(), body.expiresAt()),
                actor(actor, request));
        return ResponseEntity.created(URI.create("/api/v1/admin/students/" + created.publicId())).body(created);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentService.StudentDetail update(@PathVariable String publicId, @Valid @RequestBody UpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.update(tenant(request), publicId,
                new StudentService.UpdateCommand(body.email(), body.firstName(), body.lastName(), body.displayName(),
                        body.validFrom(), body.expiresAt(), body.version()), actor(actor, request));
    }

    @PostMapping("/{publicId}/activate")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail activate(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.activate(tenant(request), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/deactivate")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail deactivate(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.deactivate(tenant(request), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/suspend")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail suspend(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.suspend(tenant(request), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail archive(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.archive(tenant(request), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/delete")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail delete(@PathVariable String publicId,
            @Valid @RequestBody DeleteRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return service.delete(tenant(request), publicId, body.reason(), actor(actor, request));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail restore(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.restore(tenant(request), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/reset-password")
    @PreAuthorize("hasAuthority('STUDENT_PASSWORD_RESET')")
    public StudentService.StudentDetail resetPassword(@PathVariable String publicId,
            @Valid @RequestBody ResetPasswordRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return service.resetPassword(tenant(request), publicId, body.temporaryPassword(), actor(actor, request));
    }

    @GetMapping("/{publicId}/sessions")
    @PreAuthorize("hasAuthority('STUDENT_SESSION_MANAGE')")
    public List<StudentService.SessionView> sessions(@PathVariable String publicId, HttpServletRequest request) {
        return service.sessions(tenant(request), publicId);
    }

    @PostMapping("/{publicId}/sessions/{sessionPublicId}/revoke")
    @PreAuthorize("hasAuthority('STUDENT_SESSION_MANAGE')")
    public ResponseEntity<Void> revokeSession(@PathVariable String publicId, @PathVariable String sessionPublicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        service.revokeSession(tenant(request), publicId, sessionPublicId, actor(actor, request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/sessions/revoke-all")
    @PreAuthorize("hasAuthority('STUDENT_SESSION_MANAGE')")
    public ResponseEntity<Void> revokeAll(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        service.revokeAllSessions(tenant(request), publicId, actor(actor, request));
        return ResponseEntity.noContent().build();
    }

    private TenantContext tenant(HttpServletRequest request) { return tenantResolver.resolve(request); }
    private StudentService.Actor actor(AuthenticatedUser actor, HttpServletRequest request) {
        return new StudentService.Actor(actor.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request));
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String studentCode,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotBlank @Size(min = 10, max = 128) String temporaryPassword,
            @NotNull StudentStatus status,
            @NotNull Instant validFrom,
            Instant expiresAt) {}

    public record UpdateRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotNull Instant validFrom,
            Instant expiresAt,
            @NotNull Long version) {}
    public record DeleteRequest(@NotBlank @Size(max = 500) String reason) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 128) String temporaryPassword) {}
    private StudentEffectiveStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ACTIVE".equalsIgnoreCase(value)) {
            return StudentEffectiveStatus.ACTIVE;
        }
        if ("ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return StudentEffectiveStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new com.nexoskill.evaluation.shared.domain.BusinessException(
                    "STUDENT_STATUS_INVALID", "El estado indicado no es válido.");
        }
    }

}
