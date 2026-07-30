package com.nexoskill.evaluation.certifications.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.application.CertificationModels.*;
import com.nexoskill.evaluation.certifications.application.StudentCertificationService;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationLevel;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public Availability availability(HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.availability(tenantContextResolver.resolve(request), actor);
    }

    @GetMapping("/certification-catalogs")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public Catalogs catalogs(@RequestParam(required = false) String studentPublicId,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        TenantContext tenant = tenantContextResolver.resolve(request);
        return studentPublicId == null || studentPublicId.isBlank()
                ? service.catalogs(tenant, actor) : service.catalogs(tenant, studentPublicId, actor);
    }

    @GetMapping("/students/{studentPublicId}/certifications")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public StudentCertificationDetail get(@PathVariable String studentPublicId,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.get(tenantContextResolver.resolve(request), studentPublicId, actor);
    }

    @PutMapping("/students/{studentPublicId}/certifications")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public StudentCertificationDetail save(@PathVariable String studentPublicId,
            @Valid @RequestBody SaveRequest body, HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.save(tenantContextResolver.resolve(request), studentPublicId, body.toCommand(), actor,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/students/{studentPublicId}/certifications")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public CycleView createCycle(@PathVariable String studentPublicId, @Valid @RequestBody CycleRequest body,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.createCycle(tenantContextResolver.resolve(request), studentPublicId, body.toCommand(), actor,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PutMapping("/students/{studentPublicId}/certifications/{certificationId}")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public CycleView updateCycle(@PathVariable String studentPublicId, @PathVariable String certificationId,
            @Valid @RequestBody CycleRequest body, HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.updateCycle(tenantContextResolver.resolve(request), studentPublicId, certificationId,
                body.toCommand(), actor, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/students/{studentPublicId}/certifications/{certificationId}/make-primary")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public CycleView makePrimary(@PathVariable String studentPublicId, @PathVariable String certificationId,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.makePrimary(tenantContextResolver.resolve(request), studentPublicId, certificationId,
                actor, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/students/{studentPublicId}/certifications/{certificationId}/cancel")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public CycleView cancel(@PathVariable String studentPublicId, @PathVariable String certificationId,
            @RequestBody(required = false) CancelRequest body, HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.cancel(tenantContextResolver.resolve(request), studentPublicId, certificationId,
                body == null ? null : body.reason(), actor, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @GetMapping("/students/{studentPublicId}/certifications/{certificationId}/attempts")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public PageResult<AttemptView> attempts(@PathVariable String studentPublicId,
            @PathVariable String certificationId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        PaginationParameters.validate(page, size);
        return service.attempts(tenantContextResolver.resolve(request), studentPublicId, certificationId,
                page, size, actor);
    }

    @PostMapping("/students/{studentPublicId}/certifications/{certificationId}/attempts")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public AttemptView addAttempt(@PathVariable String studentPublicId, @PathVariable String certificationId,
            @Valid @RequestBody AttemptRequest body, HttpServletRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.addAttempt(tenantContextResolver.resolve(request), studentPublicId, certificationId,
                body.toCommand(), actor, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PutMapping("/students/{studentPublicId}/certifications/{certificationId}/attempts/{attemptId}")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public AttemptView updateAttempt(@PathVariable String studentPublicId, @PathVariable String certificationId,
            @PathVariable String attemptId, @Valid @RequestBody AttemptRequest body,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.updateAttempt(tenantContextResolver.resolve(request), studentPublicId, certificationId,
                attemptId, body.toCommand(), actor, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @GetMapping("/students/{studentPublicId}/certifications/history")
    @PreAuthorize("hasAuthority('STUDENT_CERTIFICATION_MANAGE') and hasAnyRole('MANAGER', 'SUPERVISOR')")
    public PageResult<HistoryView> history(@PathVariable String studentPublicId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        PaginationParameters.validate(page, size);
        return service.history(tenantContextResolver.resolve(request), studentPublicId, page, size, actor);
    }

    public record SaveRequest(List<CycleRequest> cycles) {
        SaveCommand toCommand() {
            return new SaveCommand(cycles == null ? List.of()
                    : cycles.stream().map(CycleRequest::toCommand).toList());
        }
    }

    public record CycleRequest(String publicId,
            @NotNull(message = "El tipo de certificación es obligatorio.") CertificationType type,
            String technologyPublicId, CertificationLevel certificationLevel, boolean primary,
            CertificationTrackingStatus trackingStatus, LocalDate scheduledDate, LocalDate applicationDate,
            Boolean approved, @Size(max = 1000) String actionsToTake,
            @Size(max = 1000) String softtekManagement, @Size(max = 1000) String observations,
            Boolean active, Long version, List<AttemptRequest> attempts) {
        CycleCommand toCommand() {
            return new CycleCommand(publicId, type, technologyPublicId, certificationLevel, primary,
                    trackingStatus, scheduledDate, applicationDate, approved, actionsToTake,
                    softtekManagement, observations, active == null || active, version,
                    attempts == null ? List.of() : attempts.stream().map(AttemptRequest::toCommand).toList());
        }
    }

    public record AttemptRequest(String publicId, LocalDate scheduledDate, LocalDate applicationDate,
            CertificationExamStatus examStatus,
            @DecimalMin(value = "0.0", message = "El promedio no puede ser negativo.")
            @DecimalMax(value = "100.0", message = "El promedio no puede superar 100.") BigDecimal score,
            Boolean approved, @Size(max = 1000) String result,
            @Size(max = 1000) String observations, Long version) {
        AttemptCommand toCommand() {
            return new AttemptCommand(publicId, scheduledDate, applicationDate, examStatus, score, approved,
                    result, observations, version);
        }
    }

    public record CancelRequest(@Size(max = 500) String reason) {}
}
