package com.nexoskill.evaluation.certifications.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.application.CertificationModels.*;
import com.nexoskill.evaluation.certifications.application.StudentCertificationService;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class StudentCertificationController {
    private final StudentCertificationService service;
    private final TenantContextResolver tenantContextResolver;

    public StudentCertificationController(StudentCertificationService service,
            TenantContextResolver tenantContextResolver) {
        this.service = service;
        this.tenantContextResolver = tenantContextResolver;
    }

    @GetMapping("/certification-settings/current")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE')")
    public Availability availability(HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.availability(tenantContextResolver.resolve(request), actor);
    }

    @GetMapping("/certification-catalogs")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_CATALOG_VIEW')")
    public Catalogs catalogs(HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.catalogs(tenantContextResolver.resolve(request), actor);
    }

    @GetMapping("/students/{studentPublicId}/certifications")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE')")
    public StudentCertificationDetail get(@PathVariable String studentPublicId,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.get(tenantContextResolver.resolve(request), studentPublicId, actor);
    }

    @PutMapping("/students/{studentPublicId}/certifications")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE')")
    public StudentCertificationDetail save(@PathVariable String studentPublicId,
            @Valid @RequestBody SaveRequest body, HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        TenantContext tenant = tenantContextResolver.resolve(request);
        return service.save(tenant, studentPublicId, body.toCommand(), actor,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    public record SaveRequest(
            @NotBlank(message = "Selecciona el perfil de certificación.")
            String professionalProfilePublicId,
            @NotBlank(message = "Selecciona la tecnología de certificación.")
            String certificationTechnologyPublicId,
            @NotNull(message = "La fecha de alta es obligatoria.")
            LocalDate enrollmentDate,
            @NotBlank(message = "Selecciona el perfil tecnológico.")
            String technologicalProfile,
            Long profileVersion,
            List<RequirementRequest> requirements,
            List<AttemptRequest> newAttempts) {
        SaveCommand toCommand() {
            return new SaveCommand(professionalProfilePublicId, certificationTechnologyPublicId,
                    enrollmentDate, technologicalProfile, profileVersion,
                    requirements == null ? List.of() : requirements.stream().map(RequirementRequest::toCommand).toList(),
                    newAttempts == null ? List.of() : newAttempts.stream().map(AttemptRequest::toCommand).toList());
        }
    }

    public record RequirementRequest(
            @NotNull(message = "El tipo de certificación es obligatorio.")
            com.nexoskill.evaluation.certifications.domain.CertificationType type,
            boolean applies,
            com.nexoskill.evaluation.certifications.domain.CertificationStatus certificationStatus,
            com.nexoskill.evaluation.certifications.domain.CertificationExamStatus examStatus,
            LocalDate manualDeadline,
            String deadlineOverrideReason,
            LocalDate applicationDate,
            BigDecimal score,
            Integer currentAttempt,
            String actionsToTake,
            String observations,
            Long version) {
        RequirementCommand toCommand() {
            return new RequirementCommand(type, applies, certificationStatus, examStatus,
                    manualDeadline, deadlineOverrideReason, applicationDate, score,
                    currentAttempt, actionsToTake, observations, version);
        }
    }

    public record AttemptRequest(
            @NotNull(message = "El tipo de certificación es obligatorio.")
            com.nexoskill.evaluation.certifications.domain.CertificationType type,
            int attemptNumber,
            LocalDate scheduledDate,
            LocalDate applicationDate,
            com.nexoskill.evaluation.certifications.domain.CertificationExamStatus examStatus,
            BigDecimal score,
            String result,
            String observations) {
        AttemptCommand toCommand() {
            return new AttemptCommand(type, attemptNumber, scheduledDate, applicationDate,
                    examStatus, score, result, observations);
        }
    }
}
