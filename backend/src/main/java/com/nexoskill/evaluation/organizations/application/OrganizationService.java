package com.nexoskill.evaluation.organizations.application;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.LicensePolicy;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationLicensePolicyRepository licenseRepository;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationLicensePolicyRepository licenseRepository,
                               Clock clock) {
        this.organizationRepository = organizationRepository;
        this.licenseRepository = licenseRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationJpaEntity> search(String query, OrganizationStatus status, int page, int size) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return organizationRepository.search(normalized, status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name").ascending()));
    }

    @Transactional(readOnly = true)
    public OrganizationAggregate get(String publicId) {
        OrganizationJpaEntity organization = organizationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("La organización no existe."));
        OrganizationLicensePolicyJpaEntity policy = licenseRepository.findByOrganizationId(organization.getId())
                .orElseThrow(() -> new IllegalStateException("La organización no tiene política de licencia."));
        return new OrganizationAggregate(organization, policy);
    }

    @Transactional
    public OrganizationAggregate create(CreateCommand command) {
        String code = normalizeCode(command.code());
        if (organizationRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Ya existe una organización con ese código.");
        }
        Instant now = clock.instant();
        Long actorId = actorId();
        OrganizationJpaEntity organization = OrganizationJpaEntity.create(
                UUID.randomUUID().toString(), code, required(command.name(), "nombre"), command.contentMode(),
                command.validFrom(), command.expiresOn(), actorId, now);
        organizationRepository.saveAndFlush(organization);

        LocalDate cycleStart = command.cycleStartsOn() != null ? command.cycleStartsOn() : LocalDate.now(clock);
        LocalDate cycleEnd = command.cycleEndsOn() != null ? command.cycleEndsOn() : cycleStart.plusMonths(1);
        int replacements = command.includedReplacements() != null
                ? command.includedReplacements()
                : LicensePolicy.recommendedIncludedReplacements(command.contractedSeats());
        LicensePolicy policy = new LicensePolicy(command.contractedSeats(), replacements, 0, 24, 7,
                cycleStart, cycleEnd);
        OrganizationLicensePolicyJpaEntity license = OrganizationLicensePolicyJpaEntity.create(
                organization.getId(), policy.contractedSeats(), policy.includedReplacements(),
                policy.cycleStartsOn(), policy.cycleEndsOn(), now);
        licenseRepository.save(license);
        return new OrganizationAggregate(organization, license);
    }

    @Transactional
    public OrganizationAggregate update(String publicId, UpdateCommand command) {
        OrganizationAggregate aggregate = get(publicId);
        if (!aggregate.organization().getVersion().equals(command.version())) {
            throw new IllegalStateException("La organización fue modificada por otra sesión.");
        }
        Instant now = clock.instant();
        aggregate.organization().update(required(command.name(), "nombre"), command.contentMode(),
                command.validFrom(), command.expiresOn(), actorId(), now);
        new LicensePolicy(command.contractedSeats(), command.includedReplacements(),
                command.additionalReplacements(), command.standardReleaseHours(),
                command.exhaustedReleaseDays(), command.cycleStartsOn(), command.cycleEndsOn());
        aggregate.policy().update(command.contractedSeats(), command.includedReplacements(),
                command.additionalReplacements(), command.standardReleaseHours(),
                command.exhaustedReleaseDays(), command.cycleStartsOn(), command.cycleEndsOn(), now);
        return aggregate;
    }

    @Transactional
    public OrganizationAggregate changeStatus(String publicId, OrganizationStatus status) {
        OrganizationAggregate aggregate = get(publicId);
        aggregate.organization().changeStatus(status, actorId(), clock.instant());
        return aggregate;
    }

    private Long actorId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof AuthenticatedUser user ? user.internalId() : null;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El " + field + " es obligatorio.");
        return value.trim();
    }

    private static String normalizeCode(String code) {
        return required(code, "código").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    public record OrganizationAggregate(OrganizationJpaEntity organization,
                                        OrganizationLicensePolicyJpaEntity policy) {}
    public record CreateCommand(String name, String code, ContentMode contentMode, LocalDate validFrom,
                                LocalDate expiresOn, int contractedSeats, Integer includedReplacements,
                                LocalDate cycleStartsOn, LocalDate cycleEndsOn) {}
    public record UpdateCommand(String name, ContentMode contentMode, LocalDate validFrom, LocalDate expiresOn,
                                int contractedSeats, int includedReplacements, int additionalReplacements,
                                int standardReleaseHours, int exhaustedReleaseDays,
                                LocalDate cycleStartsOn, LocalDate cycleEndsOn, Long version) {}
}
