package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import com.nexoskill.evaluation.students.application.StudentAdministrationService;
import com.nexoskill.evaluation.students.application.StudentDeletionService;
import com.nexoskill.evaluation.students.application.StudentFoundationService;
import com.nexoskill.evaluation.students.application.StudentService;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/v1/admin/students")
public class AdminStudentController {
    private final StudentService service;
    private final TenantContextResolver tenantResolver;
    private final StudentFoundationService foundation;
    private final StudentDeletionService deletion;
    private final StudentAdministrationService administration;

    /** Constructor conservado para pruebas unitarias existentes. */
    public AdminStudentController(StudentService service, TenantContextResolver tenantResolver) {
        this(service, tenantResolver, null, null, null);
    }

    public AdminStudentController(StudentService service, TenantContextResolver tenantResolver,
            StudentFoundationService foundation) {
        this(service, tenantResolver, foundation, null, null);
    }

    @Autowired
    public AdminStudentController(StudentService service, TenantContextResolver tenantResolver,
            StudentFoundationService foundation, StudentDeletionService deletion,
            StudentAdministrationService administration) {
        this.service = service;
        this.tenantResolver = tenantResolver;
        this.foundation = foundation;
        this.deletion = deletion;
        this.administration = administration;
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
        StudentEffectiveStatus parsedStatus = parseStatus(status);
        if (foundation == null) return service.search(tenant, query, parsedStatus, false, page, size);
        return foundation.search(tenant, new StudentFoundationService.SearchCriteria(query, parsedStatus,
                false, organizationPublicId, profilePublicId, technologicalProfilePublicId,
                technologyPublicId, certificationsEnabled, sort, direction), page, size);
    }

    @GetMapping("/catalogs")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentFoundationService.CatalogBundle catalogs(
            @RequestParam(required = false) String organizationPublicId, HttpServletRequest request) {
        return requireFoundation().catalogs(tenant(request), organizationPublicId);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public Object get(@PathVariable String publicId, HttpServletRequest request) {
        return foundation == null ? service.get(tenant(request), publicId) : foundation.get(tenant(request), publicId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        TenantContext resolvedTenant = tenant(request);
        if (foundation == null) {
            StudentService.CreateResult creation = service.create(resolvedTenant,
                    new StudentService.CreateCommand(body.email(), body.firstName(),
                            body.lastName(), body.displayName(),
                            body.status() == null ? StudentStatus.ACTIVE : body.status(), body.validFrom(), body.expiresAt()),
                    actor(actor, request));
            StudentService.StudentDetail created = creation.student();
            return ResponseEntity.created(URI.create("/api/v1/admin/students/" + created.publicId()))
                    .body(credentialsResponse(credentialStudent(created), resolvedTenant.organizationCode(),
                            created.email(), creation.temporaryPassword()));
        }
        StudentFoundationService.CreateResult creation = foundation.create(resolvedTenant,
                new StudentFoundationService.CreateCommand(body.organizationPublicId(), body.email(),
                        body.firstName(), body.lastName(), body.displayName(), body.status(),
                        body.validFrom(), body.expiresAt(), body.admissionDate(), body.professionalProfilePublicId(),
                        body.technologicalProfilePublicId(), Boolean.TRUE.equals(body.appliesTechnologicalCertification()),
                        Boolean.TRUE.equals(body.appliesDevelopmentSecurity()),
                        Boolean.TRUE.equals(body.appliesNormativeTesting()), Boolean.TRUE.equals(body.appliesOne()),
                        Boolean.TRUE.equals(body.appliesAgile()), Boolean.TRUE.equals(body.appliesJira())), actor(actor, request));
        StudentFoundationService.StudentView created = creation.student();
        return ResponseEntity.created(URI.create("/api/v1/admin/students/" + created.publicId()))
                .body(credentialsResponse(credentialStudent(created), created.organization().code(),
                        created.email(), creation.temporaryPassword()));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public Object update(@PathVariable String publicId, @Valid @RequestBody UpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        if (foundation == null) {
            return service.update(tenant(request), publicId,
                    new StudentService.UpdateCommand(body.email(), body.firstName(), body.lastName(), body.displayName(),
                            body.validFrom(), body.expiresAt(), body.version()), actor(actor, request));
        }
        return foundation.update(tenant(request), publicId,
                new StudentFoundationService.UpdateCommand(body.email(), body.firstName(), body.lastName(), body.displayName(),
                        body.validFrom(), body.expiresAt(), body.admissionDate(), body.professionalProfilePublicId(),
                        body.technologicalProfilePublicId(), Boolean.TRUE.equals(body.appliesTechnologicalCertification()),
                        Boolean.TRUE.equals(body.appliesDevelopmentSecurity()),
                        Boolean.TRUE.equals(body.appliesNormativeTesting()), Boolean.TRUE.equals(body.appliesOne()),
                        Boolean.TRUE.equals(body.appliesAgile()), Boolean.TRUE.equals(body.appliesJira()), body.version()), actor(actor, request));
    }

    @GetMapping("/{publicId}/administration")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentAdministrationService.AdministrationView administration(@PathVariable String publicId,
            HttpServletRequest request) {
        if (administration == null) throw new IllegalStateException("StudentAdministrationService no está disponible.");
        return administration.get(effectiveTenant(request, publicId), publicId);
    }

    @GetMapping("/{publicId}/administrative-history")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentAdministrationService.HistoryPage administrativeHistory(@PathVariable String publicId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        PaginationParameters.validate(page, size);
        if (administration == null) throw new IllegalStateException("StudentAdministrationService no está disponible.");
        return administration.history(effectiveTenant(request, publicId), publicId, page, size);
    }

    @PostMapping("/{publicId}/activate")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public Object activate(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        StudentService.StudentDetail updated = service.activate(effectiveTenant(request, publicId), publicId,
                actor(actor, request));
        return enriched(request, publicId, updated);
    }

    @PostMapping("/{publicId}/deactivate")
    @PreAuthorize("hasAuthority('STUDENT_STATUS_CHANGE')")
    public Object deactivate(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        StudentService.StudentDetail updated = service.deactivate(effectiveTenant(request, publicId), publicId,
                actor(actor, request));
        return enriched(request, publicId, updated);
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_DELETE')")
    public StudentDeletionService.DeletionResult delete(@PathVariable String publicId,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return requireDeletion().deletePermanently(effectiveTenant(request, publicId), publicId, confirmed,
                actor(actor, request));
    }

    /** Compatibilidad temporal con clientes anteriores. */
    @PostMapping("/{publicId}/permanent-delete")
    @PreAuthorize("hasAuthority('STUDENT_DELETE')")
    public StudentDeletionService.DeletionResult deletePermanently(@PathVariable String publicId,
            @Valid @RequestBody PermanentDeleteRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return requireDeletion().deletePermanently(effectiveTenant(request, publicId), publicId,
                Boolean.TRUE.equals(body.confirmed()), actor(actor, request));
    }

    @PostMapping("/{publicId}/reset-password")
    @PreAuthorize("hasAuthority('STUDENT_PASSWORD_RESET')")
    public StudentCredentialResponse resetPassword(@PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        TenantContext effective = effectiveTenant(request, publicId);
        StudentService.PasswordResetResult reset = service.resetPassword(effective, publicId, actor(actor, request));
        Object student = enriched(request, publicId, reset.student());
        String organizationLogin = effective.organizationCode();
        String email = reset.student().email();
        if (student instanceof StudentFoundationService.StudentView view) {
            organizationLogin = view.organization().code();
            email = view.email();
        }
        return credentialsResponse(credentialStudent(student), organizationLogin, email, reset.temporaryPassword());
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

    private TenantContext tenant(HttpServletRequest request) { return tenantResolver.resolve(request); }
    private TenantContext effectiveTenant(HttpServletRequest request, String publicId) {
        TenantContext resolved = tenant(request);
        return foundation == null ? resolved : foundation.effectiveTenantForStudent(resolved, publicId);
    }
    private StudentFoundationService requireFoundation() {
        if (foundation == null) throw new IllegalStateException("StudentFoundationService no está disponible.");
        return foundation;
    }
    private StudentDeletionService requireDeletion() {
        if (deletion == null) throw new IllegalStateException("StudentDeletionService no está disponible.");
        return deletion;
    }
    private Object enriched(HttpServletRequest request, String publicId, StudentService.StudentDetail fallback) {
        return foundation == null ? fallback : foundation.get(tenant(request), publicId);
    }
    private StudentCredentialResponse credentialsResponse(StudentCredentialStudentResponse student,
            String organizationLogin, String email, String temporaryPassword) {
        return new StudentCredentialResponse(student,
                new TemporaryCredentialsResponse(organizationLogin, email, temporaryPassword, true));
    }

    private StudentCredentialStudentResponse credentialStudent(Object student) {
        if (student instanceof StudentFoundationService.StudentView view) {
            return new StudentCredentialStudentResponse(view.publicId(), view.studentCode(), view.displayName(), view.email(), view.status());
        }
        if (student instanceof StudentService.StudentDetail detail) {
            return new StudentCredentialStudentResponse(detail.publicId(), detail.studentCode(), detail.displayName(), detail.email(), detail.status());
        }
        throw new IllegalStateException("No fue posible construir la respuesta de credenciales del estudiante.");
    }

    private StudentService.Actor actor(AuthenticatedUser actor, HttpServletRequest request) {
        return new StudentService.Actor(actor.internalId(), ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request));
    }

    public record CreateRequest(String organizationPublicId,
            @NotBlank(message = "El correo electrónico es obligatorio.")
            @Email(message = "El correo electrónico no tiene un formato válido.")
            @Size(max = 254, message = "El correo no puede superar 254 caracteres.") String email,
            @Size(max = 100, message = "El nombre no puede superar 100 caracteres.") String firstName,
            @Size(max = 150, message = "Los apellidos no pueden superar 150 caracteres.") String lastName,
            @Size(max = 250, message = "El nombre completo no puede superar 250 caracteres.") String displayName,
            StudentStatus status,
            @NotNull(message = "El inicio de vigencia es obligatorio.") LocalDate validFrom,
            @NotNull(message = "La fecha de vencimiento es obligatoria.") LocalDate expiresAt,
            LocalDate admissionDate, String professionalProfilePublicId, String technologicalProfilePublicId,
            Boolean appliesTechnologicalCertification, Boolean appliesDevelopmentSecurity,
            Boolean appliesNormativeTesting, Boolean appliesOne, Boolean appliesAgile, Boolean appliesJira) {}

    public record UpdateRequest(
            @NotBlank(message = "El correo electrónico es obligatorio.")
            @Email(message = "El correo electrónico no tiene un formato válido.")
            @Size(max = 254, message = "El correo no puede superar 254 caracteres.") String email,
            @Size(max = 100, message = "El nombre no puede superar 100 caracteres.") String firstName,
            @Size(max = 150, message = "Los apellidos no pueden superar 150 caracteres.") String lastName,
            @Size(max = 250, message = "El nombre completo no puede superar 250 caracteres.") String displayName,
            @NotNull(message = "El inicio de vigencia es obligatorio.") LocalDate validFrom,
            @NotNull(message = "La fecha de vencimiento es obligatoria.") LocalDate expiresAt,
            LocalDate admissionDate, String professionalProfilePublicId, String technologicalProfilePublicId,
            Boolean appliesTechnologicalCertification, Boolean appliesDevelopmentSecurity,
            Boolean appliesNormativeTesting, Boolean appliesOne, Boolean appliesAgile, Boolean appliesJira,
            @NotNull(message = "La versión del estudiante es obligatoria.") Long version) {}

    public record StudentCredentialResponse(StudentCredentialStudentResponse student,
            TemporaryCredentialsResponse temporaryCredentials) {}
    public record StudentCredentialStudentResponse(String publicId, String studentCode, String fullName, String email,
            StudentStatus status) {}
    public record TemporaryCredentialsResponse(String organizationLogin, String email,
            String temporaryPassword, boolean mustChangePassword) {}

    public record PermanentDeleteRequest(
            @NotNull(message = "Debes confirmar la eliminación permanente.")
            @AssertTrue(message = "Debes confirmar que comprendes que la eliminación es permanente.") Boolean confirmed) {}

    private StudentEffectiveStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ACTIVE".equalsIgnoreCase(value)) return StudentEffectiveStatus.ACTIVE;
        if ("ALL".equalsIgnoreCase(value)) return null;
        try {
            StudentEffectiveStatus parsed = StudentEffectiveStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (parsed == StudentEffectiveStatus.DELETED) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("STUDENT_STATUS_INVALID", "El estado indicado no es válido.");
        }
    }
}
