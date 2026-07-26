package com.nexoskill.evaluation.organizations.application;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.LicensePolicy;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationLicensePolicyRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
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
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by("name").ascending()));
    }

    @Transactional(readOnly = true)
    public OrganizationAggregate get(String publicId) {
        String normalizedPublicId = PublicIdNormalizer.requiredUuid(publicId,
                "ORGANIZATION_NOT_FOUND", "La organización solicitada no existe.");
        OrganizationJpaEntity organization = organizationRepository.findByPublicId(normalizedPublicId)
                .orElseThrow(() -> error("ORGANIZATION_NOT_FOUND", "La organización solicitada no existe."));
        OrganizationLicensePolicyJpaEntity policy = licenseRepository.findByOrganizationId(organization.getId())
                .orElseThrow(() -> error("ORGANIZATION_LICENSE_NOT_FOUND",
                        "La organización no tiene una política de licenciamiento configurada."));
        return new OrganizationAggregate(organization, policy);
    }

    @Transactional
    public OrganizationAggregate create(CreateCommand command) {
        String code = normalizeCode(command.code());
        if (organizationRepository.existsByCode(code)) {
            throw error("ORGANIZATION_CODE_EXISTS", "Ya existe una organización con ese código.");
        }

        String name = required(command.name(), "nombre");
        ContentMode contentMode = requiredContentMode(command.contentMode());
        validateValidity(command.validFrom(), command.expiresOn());
        LicensePolicy policy = createPolicy(command);

        Instant now = clock.instant();
        Long actorId = actorId();
        OrganizationJpaEntity organization = OrganizationJpaEntity.create(
                UUID.randomUUID().toString(), code, name, contentMode,
                command.validFrom(), command.expiresOn(), actorId, now);
        organizationRepository.saveAndFlush(organization);

        OrganizationLicensePolicyJpaEntity license = OrganizationLicensePolicyJpaEntity.create(
                organization.getId(), policy.contractedSeats(), policy.includedReplacements(),
                policy.additionalReplacements(), policy.standardReleaseHours(),
                policy.exhaustedReplacementReleaseDays(), policy.cycleStartsOn(),
                policy.cycleEndsOn(), now);
        licenseRepository.saveAndFlush(license);
        return new OrganizationAggregate(organization, license);
    }

    @Transactional
    public OrganizationAggregate update(String publicId, UpdateCommand command) {
        OrganizationAggregate aggregate = get(publicId);
        if (command.version() == null || !Objects.equals(aggregate.organization().getVersion(), command.version())) {
            throw error("ORGANIZATION_CONCURRENT_MODIFICATION",
                    "La organización fue modificada por otra sesión. Actualiza la página e intenta nuevamente.");
        }

        String name = required(command.name(), "nombre");
        ContentMode contentMode = requiredContentMode(command.contentMode());
        validateValidity(command.validFrom(), command.expiresOn());
        LicensePolicy policy = updatePolicy(command);
        Instant now = clock.instant();

        aggregate.organization().update(name, contentMode,
                command.validFrom(), command.expiresOn(), actorId(), now);
        aggregate.policy().update(policy.contractedSeats(), policy.includedReplacements(),
                policy.additionalReplacements(), policy.standardReleaseHours(),
                policy.exhaustedReplacementReleaseDays(), policy.cycleStartsOn(),
                policy.cycleEndsOn(), now);
        organizationRepository.flush();
        licenseRepository.flush();
        return aggregate;
    }

    @Transactional
    public OrganizationAggregate changeStatus(String publicId, OrganizationStatus status) {
        if (status == null) {
            throw error("ORGANIZATION_STATUS_REQUIRED", "Selecciona un estado válido para la organización.");
        }
        OrganizationAggregate aggregate = get(publicId);
        aggregate.organization().changeStatus(status, actorId(), clock.instant());
        organizationRepository.flush();
        return aggregate;
    }

    private LicensePolicy createPolicy(CreateCommand command) {
        int seats = requiredNonNegative(command.contractedSeats(), "Los asientos contratados");
        int included = command.includedReplacements() == null
                ? LicensePolicy.recommendedIncludedReplacements(seats)
                : nonNegative(command.includedReplacements(), "Las sustituciones incluidas");
        int additional = command.additionalReplacements() == null
                ? 0
                : nonNegative(command.additionalReplacements(), "Las sustituciones adicionales");
        int releaseHours = command.standardReleaseHours() == null
                ? 24
                : atLeast(command.standardReleaseHours(), 1, "La liberación estándar");
        int exhaustedDays = command.exhaustedReleaseDays() == null
                ? 7
                : nonNegative(command.exhaustedReleaseDays(), "El bloqueo antifraude");
        LocalDate cycleStart = command.cycleStartsOn() == null
                ? LocalDate.now(clock)
                : command.cycleStartsOn();
        LocalDate cycleEnd = command.cycleEndsOn() == null
                ? cycleStart.plusMonths(1)
                : command.cycleEndsOn();

        return buildPolicy(seats, included, additional, releaseHours, exhaustedDays, cycleStart, cycleEnd);
    }

    private LicensePolicy updatePolicy(UpdateCommand command) {
        int seats = requiredNonNegative(command.contractedSeats(), "Los asientos contratados");
        int included = requiredNonNegative(command.includedReplacements(), "Las sustituciones incluidas");
        int additional = requiredNonNegative(command.additionalReplacements(), "Las sustituciones adicionales");
        int releaseHours = requiredAtLeast(command.standardReleaseHours(), 1, "La liberación estándar");
        int exhaustedDays = requiredNonNegative(command.exhaustedReleaseDays(), "El bloqueo antifraude");
        if (command.cycleStartsOn() == null || command.cycleEndsOn() == null) {
            throw error("ORGANIZATION_LICENSE_CYCLE_REQUIRED",
                    "El inicio y el fin del ciclo de sustituciones son obligatorios.");
        }
        return buildPolicy(seats, included, additional, releaseHours, exhaustedDays,
                command.cycleStartsOn(), command.cycleEndsOn());
    }

    private LicensePolicy buildPolicy(int seats, int included, int additional, int releaseHours,
                                      int exhaustedDays, LocalDate cycleStart, LocalDate cycleEnd) {
        try {
            return new LicensePolicy(seats, included, additional, releaseHours, exhaustedDays,
                    cycleStart, cycleEnd);
        } catch (IllegalArgumentException exception) {
            throw error("ORGANIZATION_LICENSE_POLICY_INVALID", exception.getMessage());
        }
    }

    private Long actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? user.internalId() : null;
    }

    private static void validateValidity(LocalDate validFrom, LocalDate expiresOn) {
        if (validFrom != null && expiresOn != null && expiresOn.isBefore(validFrom)) {
            throw error("ORGANIZATION_VALIDITY_INVALID",
                    "La fecha de vencimiento no puede ser anterior al inicio de vigencia.");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw error("ORGANIZATION_FIELD_REQUIRED", "El " + field + " es obligatorio.");
        }
        return value.trim();
    }

    private static ContentMode requiredContentMode(ContentMode contentMode) {
        if (contentMode == null) {
            throw error("ORGANIZATION_CONTENT_MODE_REQUIRED", "Selecciona una modalidad de contenido.");
        }
        return contentMode;
    }

    private static int requiredNonNegative(Integer value, String field) {
        if (value == null) {
            throw error("ORGANIZATION_LICENSE_FIELD_REQUIRED",
                    "Debes indicar " + field.toLowerCase(Locale.ROOT) + ".");
        }
        return nonNegative(value, field);
    }

    private static int requiredAtLeast(Integer value, int minimum, String field) {
        if (value == null) {
            throw error("ORGANIZATION_LICENSE_FIELD_REQUIRED",
                    "Debes indicar " + field.toLowerCase(Locale.ROOT) + ".");
        }
        return atLeast(value, minimum, field);
    }

    private static int nonNegative(int value, String field) {
        return atLeast(value, 0, field);
    }

    private static int atLeast(int value, int minimum, String field) {
        if (value < minimum) {
            throw error("ORGANIZATION_LICENSE_VALUE_INVALID",
                    field + " debe ser mayor o igual a " + minimum + ".");
        }
        return value;
    }

    private static String normalizeCode(String code) {
        String normalized = required(code, "código")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw error("ORGANIZATION_CODE_INVALID",
                    "El código debe contener al menos una letra o un número.");
        }
        if (normalized.length() > 80) {
            throw error("ORGANIZATION_CODE_INVALID", "El código no puede superar 80 caracteres.");
        }
        return normalized;
    }

    private static BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }

    public record OrganizationAggregate(OrganizationJpaEntity organization,
                                        OrganizationLicensePolicyJpaEntity policy) {}

    public record CreateCommand(String name, String code, ContentMode contentMode, LocalDate validFrom,
                                LocalDate expiresOn, Integer contractedSeats, Integer includedReplacements,
                                Integer additionalReplacements, Integer standardReleaseHours,
                                Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
                                LocalDate cycleEndsOn) {}

    public record UpdateCommand(String name, ContentMode contentMode, LocalDate validFrom, LocalDate expiresOn,
                                Integer contractedSeats, Integer includedReplacements,
                                Integer additionalReplacements, Integer standardReleaseHours,
                                Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
                                LocalDate cycleEndsOn, Long version) {}
}
