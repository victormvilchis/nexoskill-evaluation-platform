package com.nexoskill.evaluation.organizations.interfaces.rest;

import com.nexoskill.evaluation.organizations.application.OrganizationService;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyJpaEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/organizations")
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public PageResponse list(@RequestParam(required = false) String query,
                             @RequestParam(required = false) OrganizationStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        Page<OrganizationJpaEntity> result = service.search(query, status, page, size);
        return new PageResponse(result.getContent().stream().map(this::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_VIEW')")
    public OrganizationResponse get(@PathVariable String publicId) {
        return response(service.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORGANIZATION_CREATE')")
    public OrganizationResponse create(@Valid @RequestBody CreateRequest request) {
        return response(service.create(new OrganizationService.CreateCommand(request.name(), request.code(),
                request.contentMode(), request.validFrom(), request.expiresOn(), request.contractedSeats(),
                request.includedReplacements(), request.cycleStartsOn(), request.cycleEndsOn())));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_UPDATE')")
    public OrganizationResponse update(@PathVariable String publicId, @Valid @RequestBody UpdateRequest request) {
        return response(service.update(publicId, new OrganizationService.UpdateCommand(request.name(),
                request.contentMode(), request.validFrom(), request.expiresOn(), request.contractedSeats(),
                request.includedReplacements(), request.additionalReplacements(), request.standardReleaseHours(),
                request.exhaustedReleaseDays(), request.cycleStartsOn(), request.cycleEndsOn(), request.version())));
    }

    @PostMapping("/{publicId}/status/{status}")
    @PreAuthorize("hasAuthority('ORGANIZATION_STATUS_CHANGE')")
    public OrganizationResponse changeStatus(@PathVariable String publicId, @PathVariable OrganizationStatus status) {
        return response(service.changeStatus(publicId, status));
    }

    private OrganizationSummary summary(OrganizationJpaEntity entity) {
        return new OrganizationSummary(entity.getPublicId(), entity.getCode(), entity.getName(), entity.getStatus(),
                entity.getContentMode(), entity.getExpiresOn(), entity.getUpdatedAt());
    }

    private OrganizationResponse response(OrganizationService.OrganizationAggregate aggregate) {
        OrganizationJpaEntity o = aggregate.organization();
        OrganizationLicensePolicyJpaEntity p = aggregate.policy();
        return new OrganizationResponse(o.getPublicId(), o.getCode(), o.getName(), o.getStatus(), o.getContentMode(),
                o.getValidFrom(), o.getExpiresOn(), p.getContractedSeats(), p.getIncludedReplacements(),
                p.getAdditionalReplacements(), p.getStandardReleaseHours(), p.getExhaustedReleaseDays(),
                p.getCycleStartsOn(), p.getCycleEndsOn(), o.getCreatedAt(), o.getUpdatedAt(), o.getVersion());
    }

    public record CreateRequest(@NotBlank @Size(max = 200) String name,
                                @NotBlank @Size(max = 80) String code,
                                @NotNull ContentMode contentMode,
                                LocalDate validFrom,
                                LocalDate expiresOn,
                                @Min(0) int contractedSeats,
                                @Min(0) Integer includedReplacements,
                                LocalDate cycleStartsOn,
                                LocalDate cycleEndsOn) {}

    public record UpdateRequest(@NotBlank @Size(max = 200) String name,
                                @NotNull ContentMode contentMode,
                                LocalDate validFrom,
                                LocalDate expiresOn,
                                @Min(0) int contractedSeats,
                                @Min(0) int includedReplacements,
                                @Min(0) int additionalReplacements,
                                @Min(1) int standardReleaseHours,
                                @Min(0) int exhaustedReleaseDays,
                                @NotNull LocalDate cycleStartsOn,
                                @NotNull LocalDate cycleEndsOn,
                                @NotNull Long version) {}

    public record OrganizationSummary(String publicId, String code, String name, OrganizationStatus status,
                                      ContentMode contentMode, LocalDate expiresOn, java.time.Instant updatedAt) {}
    public record OrganizationResponse(String publicId, String code, String name, OrganizationStatus status,
                                       ContentMode contentMode, LocalDate validFrom, LocalDate expiresOn,
                                       int contractedSeats, int includedReplacements, int additionalReplacements,
                                       int standardReleaseHours, int exhaustedReleaseDays,
                                       LocalDate cycleStartsOn, LocalDate cycleEndsOn,
                                       java.time.Instant createdAt, java.time.Instant updatedAt, Long version) {}
    public record PageResponse(List<OrganizationSummary> content, int page, int size, long totalElements,
                               int totalPages) {}
}
