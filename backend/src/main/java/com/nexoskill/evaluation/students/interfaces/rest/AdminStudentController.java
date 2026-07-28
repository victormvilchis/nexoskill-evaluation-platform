package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import com.nexoskill.evaluation.students.application.StudentFoundationService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final StudentFoundationService foundation;

    /** Constructor conservado para pruebas unitarias existentes. */
    public AdminStudentController(StudentService service, TenantContextResolver tenantResolver) {
        this(service, tenantResolver, null);
    }

    @Autowired
    public AdminStudentController(StudentService service, TenantContextResolver tenantResolver,
            StudentFoundationService foundation) {
        this.service = service;
        this.tenantResolver = tenantResolver;
        this.foundation = foundation;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public Object search(HttpServletRequest request, @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) String organizationPublicId,
            @RequestParam(required = false) String profilePublicId,
            @RequestParam(required = false) String technologicalProfilePublicId,
            @RequestParam(required = false) String technologyPublicId,
            @RequestParam(required = false) Boolean certificationsEnabled,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationParameters.validate(page, size);
        TenantContext tenant = tenant(request);
        if (foundation == null) {
            return service.search(tenant, query, parseStatus(status), includeDeleted, page, size);
        }
        return foundation.search(tenant, new StudentFoundationService.SearchCriteria(query, parseStatus(status),
                includeDeleted, organizationPublicId, profilePublicId, technologicalProfilePublicId,
                technologyPublicId, certificationsEnabled, sort, direction), page, size);
    }

    @GetMapping("/catalogs")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentFoundationService.CatalogBundle catalogs() {
        return requireFoundation().catalogs();
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public Object get(@PathVariable String publicId, HttpServletRequest request) {
        return foundation == null ? service.get(tenant(request), publicId)
                : foundation.get(tenant(request), publicId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        if (foundation == null) {
            StudentService.StudentDetail created = service.create(tenant(request),
                    new StudentService.CreateCommand(body.studentCode(), body.email(), body.firstName(),
                            body.lastName(), body.displayName(), body.temporaryPassword(),
                            body.status() == null ? StudentStatus.ACTIVE : body.status(), body.validFrom(),
                            body.expiresAt()), actor(actor, request));
            return ResponseEntity.created(URI.create("/api/v1/admin/students/" + created.publicId())).body(created);
        }
        StudentFoundationService.StudentView created = foundation.create(tenant(request),
                new StudentFoundationService.CreateCommand(body.organizationPublicId(), body.studentCode(),
                        body.email(), body.firstName(), body.lastName(), body.displayName(), body.temporaryPassword(),
                        body.status(), body.validFrom(), body.expiresAt(), body.professionalProfilePublicId(),
                        body.technologicalProfilePublicId(), body.technologyPublicId(),
                        body.certificationEnrollmentDate()), actor(actor, request));
        return ResponseEntity.created(URI.create("/api/v1/admin/students/" + created.publicId())).body(created);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public Object update(@PathVariable String publicId, @Valid @RequestBody UpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        if (foundation == null) {
            return service.update(tenant(request), publicId,
                    new StudentService.UpdateCommand(body.email(), body.firstName(), body.lastName(),
                            body.displayName(), body.validFrom(), body.expiresAt(), body.version()),
                    actor(actor, request));
        }
        return foundation.update(tenant(request), publicId,
                new StudentFoundationService.UpdateCommand(body.email(), body.firstName(), body.lastName(),
                        body.displayName(), body.validFrom(), body.expiresAt(),
                        body.professionalProfilePublicId(), body.technologicalProfilePublicId(),
                        body.technologyPublicId(), body.certificationEnrollmentDate(), body.version()),
                actor(actor, request));
    }

    @PostMapping("/{publicId}/activate")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail activate(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.activate(effectiveTenant(request, publicId), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/deactivate")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail deactivate(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.deactivate(effectiveTenant(request, publicId), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/suspend")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail suspend(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.suspend(effectiveTenant(request, publicId), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail archive(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.archive(effectiveTenant(request, publicId), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/delete")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail delete(@PathVariable String publicId, @Valid @RequestBody DeleteRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.delete(effectiveTenant(request, publicId), publicId, body.reason(), actor(actor, request));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public StudentService.StudentDetail restore(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.restore(effectiveTenant(request, publicId), publicId, actor(actor, request));
    }

    @PostMapping("/{publicId}/reset-password")
    @PreAuthorize("hasAuthority('STUDENT_PASSWORD_RESET')")
    public StudentService.StudentDetail resetPassword(@PathVariable String publicId,
            @Valid @RequestBody ResetPasswordRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return service.resetPassword(effectiveTenant(request, publicId), publicId, body.temporaryPassword(),
                actor(actor, request));
    }

    @GetMapping("/{publicId}/sessions")
    @PreAuthorize("hasAuthority('STUDENT_SESSION_MANAGE')")
    public List<StudentService.SessionView> sessions(@PathVariable String publicId, HttpServletRequest request) {
        return service.sessions(effectiveTenant(request, publicId), publicId);
    }

    @PostMapping("/{publicId}/sessions/{sessionPublicId}/revoke")
    @PreAuthorize("hasAuthority('STUDENT_SESSION_MANAGE')")
    public ResponseEntity<Void> revokeSession(@PathVariable String publicId, @PathVariable String sessionPublicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        service.revokeSession(effectiveTenant(request, publicId), publicId, sessionPublicId, actor(actor, request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/sessions/revoke-all")
    @PreAuthorize("hasAuthority('STUDENT_SESSION_MANAGE')")
    public ResponseEntity<Void> revokeAll(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        service.revokeAllSessions(effectiveTenant(request, publicId), publicId, actor(actor, request));
        return ResponseEntity.noContent().build();
    }

    private TenantContext tenant(HttpServletRequest request) {
        return tenantResolver.resolve(request);
    }

    private TenantContext effectiveTenant(HttpServletRequest request, String publicId) {
        TenantContext resolved = tenant(request);
        return foundation == null ? resolved : foundation.effectiveTenantForStudent(resolved, publicId);
    }

    private StudentFoundationService requireFoundation() {
        if (foundation == null) throw new IllegalStateException("StudentFoundationService no está disponible.");
        return foundation;
    }

    private StudentService.Actor actor(AuthenticatedUser actor, HttpServletRequest request) {
        return new StudentService.Actor(actor.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request));
    }

    public record CreateRequest(String organizationPublicId,
            @NotBlank @Size(max = 80) String studentCode,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotBlank @Size(min = 10, max = 128) String temporaryPassword,
            StudentStatus status,
            @NotNull Instant validFrom,
            Instant expiresAt,
            String professionalProfilePublicId,
            String technologicalProfilePublicId,
            String technologyPublicId,
            LocalDate certificationEnrollmentDate) {}

    public record UpdateRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotNull Instant validFrom,
            Instant expiresAt,
            String professionalProfilePublicId,
            String technologicalProfilePublicId,
            String technologyPublicId,
            LocalDate certificationEnrollmentDate,
            @NotNull Long version) {}

    public record DeleteRequest(@NotBlank @Size(max = 500) String reason) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 128) String temporaryPassword) {}

    private StudentEffectiveStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ACTIVE".equalsIgnoreCase(value)) return StudentEffectiveStatus.ACTIVE;
        if ("ALL".equalsIgnoreCase(value)) return null;
        try {
            return StudentEffectiveStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new com.nexoskill.evaluation.shared.domain.BusinessException("STUDENT_STATUS_INVALID",
                    "El estado indicado no es válido.");
        }
    }
}
