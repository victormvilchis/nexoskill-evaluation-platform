package com.nexoskill.evaluation.organizations.application;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.OrganizationCertificationPolicyJpaEntity;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.OrganizationCertificationPolicyRepository;
import com.nexoskill.evaluation.organizations.domain.model.ContentMode;
import com.nexoskill.evaluation.organizations.domain.model.LicensePolicy;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AuditLogPort auditLogPort;
    private final OrganizationCertificationPolicyRepository certificationPolicyRepository;
    private final Clock clock;

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationLicensePolicyRepository licenseRepository,
                               AuditLogPort auditLogPort,
                               OrganizationCertificationPolicyRepository certificationPolicyRepository,
                               Clock clock) {
        this.organizationRepository = organizationRepository;
        this.licenseRepository = licenseRepository;
        this.auditLogPort = auditLogPort;
        this.certificationPolicyRepository = certificationPolicyRepository;
        this.clock = clock;
    }

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationLicensePolicyRepository licenseRepository,
                               Clock clock) {
        this(organizationRepository, licenseRepository,
                (userId, eventType, moduleCode, description, ipAddress, userAgent, eventData, occurredAt) -> { },
                null, clock);
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
                .orElse(null);
        if (organization.getOrganizationType() == OrganizationType.CUSTOMER && policy == null) {
            throw error("ORGANIZATION_LICENSE_NOT_FOUND",
                    "La organización comercial no tiene una política de licenciamiento configurada.");
        }
        if (organization.getOrganizationType() == OrganizationType.GLOBAL && policy != null) {
            throw error("GLOBAL_ORGANIZATION_LICENSE_INVALID",
                    "La organización global no utiliza políticas de licenciamiento.");
        }
        return new OrganizationAggregate(organization, policy);
    }

    @Transactional
    public OrganizationAggregate create(CreateCommand command) {
        String code = normalizeCode(command.code());
        if (OrganizationJpaEntity.GLOBAL_CODE.equals(code)) {
            throw error("GLOBAL_ORGANIZATION_RESERVED", "El código GLOBAL está reservado por el sistema.");
        }
        if (organizationRepository.existsByCode(code)) {
            throw error("ORGANIZATION_CODE_EXISTS", "Ya existe una organización con ese código.");
        }

        String name = required(command.name(), "nombre");
        ContentMode contentMode = requiredContentMode(command.contentMode());
        LocalDate validFrom = LocalDate.now(clock);
        validateExpiration(validFrom, command.expiresOn());
        LicensePolicy policy = createPolicy(command, validFrom);

        Instant now = clock.instant();
        Long actorId = actorId();
        OrganizationJpaEntity organizationToCreate = OrganizationJpaEntity.createCustomer(
                UUID.randomUUID().toString(), code, name, contentMode,
                validFrom, command.expiresOn(), actorId, now);
        if (Boolean.TRUE.equals(command.appliesCertifications())) {
            organizationToCreate.configureCertifications(true, actorId, now);
        }

        // saveAndFlush puede devolver una instancia administrada distinta cuando JPA usa merge.
        // Siempre se debe continuar con la instancia retornada por el repositorio.
        OrganizationJpaEntity organization = organizationRepository.saveAndFlush(organizationToCreate);
        if (organization == null || organization.getId() == null) {
            throw error("ORGANIZATION_PERSISTENCE_INVALID",
                    "No fue posible obtener el identificador de la organización antes de crear su licencia.");
        }

        OrganizationLicensePolicyJpaEntity licenseToCreate = OrganizationLicensePolicyJpaEntity.create(
                organization.getId(), policy.contractedSeats(), policy.includedReplacements(),
                policy.additionalReplacements(), policy.standardReleaseHours(),
                policy.exhaustedReplacementReleaseDays(), policy.cycleStartsOn(),
                policy.cycleEndsOn(), now);
        OrganizationLicensePolicyJpaEntity license = licenseRepository.saveAndFlush(licenseToCreate);
        if (license == null || !Objects.equals(organization.getId(), license.getOrganizationId())) {
            throw error("ORGANIZATION_LICENSE_PERSISTENCE_INVALID",
                    "No fue posible asociar correctamente la licencia con la organización creada.");
        }
        initializeCertificationPolicies(organization.getId(), actorId, now);
        if (organization.isAppliesCertifications()) {
            auditCertificationSetting(organization, false, true, actorId, now);
        }
        return new OrganizationAggregate(organization, license);
    }

    @Transactional
    public OrganizationAggregate update(String publicId, UpdateCommand command) {
        OrganizationAggregate aggregate = get(publicId);
        ensureCustomer(aggregate.organization());
        if (command.version() == null || !Objects.equals(aggregate.organization().getVersion(), command.version())) {
            throw error("ORGANIZATION_CONCURRENT_MODIFICATION",
                    "La organización fue modificada por otra sesión. Actualiza la página e intenta nuevamente.");
        }

        String name = required(command.name(), "nombre");
        ContentMode contentMode = requiredContentMode(command.contentMode());
        validateExpiration(aggregate.organization().getValidFrom(), command.expiresOn());
        LicensePolicy policy = updatePolicy(command);
        Instant now = clock.instant();

        Long actorId = actorId();
        boolean previousCertificationSetting = aggregate.organization().isAppliesCertifications();
        boolean requestedCertificationSetting = command.appliesCertifications() == null
                ? previousCertificationSetting
                : Boolean.TRUE.equals(command.appliesCertifications());
        aggregate.organization().updateCustomer(name, contentMode, command.expiresOn(), actorId, now);
        aggregate.organization().configureCertifications(requestedCertificationSetting, actorId, now);
        aggregate.policy().update(policy.contractedSeats(), policy.includedReplacements(),
                policy.additionalReplacements(), policy.standardReleaseHours(),
                policy.exhaustedReplacementReleaseDays(), policy.cycleStartsOn(),
                policy.cycleEndsOn(), now);
        organizationRepository.flush();
        licenseRepository.flush();
        if (previousCertificationSetting != aggregate.organization().isAppliesCertifications()) {
            auditCertificationSetting(aggregate.organization(), previousCertificationSetting,
                    aggregate.organization().isAppliesCertifications(), actorId, now);
        }
        return aggregate;
    }

    @Transactional
    public OrganizationAggregate changeStatus(String publicId, OrganizationStatus status) {
        if (status == null) {
            throw error("ORGANIZATION_STATUS_REQUIRED", "Selecciona un estado válido para la organización.");
        }
        OrganizationAggregate aggregate = get(publicId);
        ensureCustomer(aggregate.organization());
        aggregate.organization().changeStatus(status, actorId(), clock.instant());
        organizationRepository.flush();
        return aggregate;
    }

    private void initializeCertificationPolicies(Long organizationId, Long actorId, Instant now) {
        if (certificationPolicyRepository == null) return;
        for (CertificationType type : CertificationType.values()) {
            if (certificationPolicyRepository.findByOrganizationIdAndCertificationType(organizationId, type).isPresent()) {
                continue;
            }
            Integer months = type == CertificationType.DEVELOPMENT_SECURITY
                    || type == CertificationType.NORMATIVE_TESTING ? 2 : null;
            Integer days = type == CertificationType.DEVELOPMENT_SECURITY ? 15
                    : type == CertificationType.NORMATIVE_TESTING ? 0 : null;
            certificationPolicyRepository.save(OrganizationCertificationPolicyJpaEntity.create(
                    organizationId, type, months, days, actorId, now));
        }
        certificationPolicyRepository.flush();
    }

    private void auditCertificationSetting(OrganizationJpaEntity organization, boolean previous,
                                             boolean current, Long actorId, Instant now) {
        java.util.HashMap<String, Object> data = new java.util.HashMap<>();
        data.put("organizationPublicId", organization.getPublicId());
        data.put("previousAppliesCertifications", previous);
        data.put("appliesCertifications", current);
        auditLogPort.record(actorId,
                current ? "ORGANIZATION_CERTIFICATIONS_ENABLED" : "ORGANIZATION_CERTIFICATIONS_DISABLED",
                "ORGANIZATIONS",
                current ? "Se habilitó la gestión de certificaciones para la organización."
                        : "Se deshabilitó la gestión de certificaciones para la organización.",
                null, null, data, now);
    }

    private void ensureCustomer(OrganizationJpaEntity organization) {
        if (organization.getOrganizationType() == OrganizationType.GLOBAL) {
            throw error("GLOBAL_ORGANIZATION_IMMUTABLE",
                    "La organización global no puede editarse, desactivarse ni eliminarse.");
        }
    }

    private LicensePolicy createPolicy(CreateCommand command, LocalDate organizationStart) {
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
        LocalDate cycleStart = command.cycleStartsOn() == null ? organizationStart : command.cycleStartsOn();
        LocalDate cycleEnd = command.cycleEndsOn() == null ? cycleStart.plusMonths(1) : command.cycleEndsOn();
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
            return new LicensePolicy(seats, included, additional, releaseHours, exhaustedDays, cycleStart, cycleEnd);
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

    private static void validateExpiration(LocalDate validFrom, LocalDate expiresOn) {
        if (expiresOn != null && expiresOn.isBefore(validFrom)) {
            throw error("ORGANIZATION_VALIDITY_INVALID",
                    "La fecha de vencimiento no puede ser anterior a la fecha de creación.");
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

    private static int nonNegative(int value, String field) { return atLeast(value, 0, field); }

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
            throw error("ORGANIZATION_CODE_INVALID", "El código debe contener al menos una letra o un número.");
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

    public record CreateCommand(String name, String code, ContentMode contentMode, LocalDate expiresOn,
                                Integer contractedSeats, Integer includedReplacements,
                                Integer additionalReplacements, Integer standardReleaseHours,
                                Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
                                LocalDate cycleEndsOn, Boolean appliesCertifications) {
        public CreateCommand(String name, String code, ContentMode contentMode, LocalDate expiresOn,
                             Integer contractedSeats, Integer includedReplacements,
                             Integer additionalReplacements, Integer standardReleaseHours,
                             Integer exhaustedReleaseDays, LocalDate cycleStartsOn, LocalDate cycleEndsOn) {
            this(name, code, contentMode, expiresOn, contractedSeats, includedReplacements,
                    additionalReplacements, standardReleaseHours, exhaustedReleaseDays,
                    cycleStartsOn, cycleEndsOn, false);
        }

        public CreateCommand(String name, String code, ContentMode contentMode, LocalDate ignoredValidFrom,
                             LocalDate expiresOn, Integer contractedSeats, Integer includedReplacements,
                             Integer additionalReplacements, Integer standardReleaseHours,
                             Integer exhaustedReleaseDays, LocalDate cycleStartsOn, LocalDate cycleEndsOn) {
            this(name, code, contentMode, expiresOn, contractedSeats, includedReplacements,
                    additionalReplacements, standardReleaseHours, exhaustedReleaseDays,
                    cycleStartsOn, cycleEndsOn, false);
        }
    }

    public record UpdateCommand(String name, ContentMode contentMode, LocalDate expiresOn,
                                Integer contractedSeats, Integer includedReplacements,
                                Integer additionalReplacements, Integer standardReleaseHours,
                                Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
                                LocalDate cycleEndsOn, Long version, Boolean appliesCertifications) {
        public UpdateCommand(String name, ContentMode contentMode, LocalDate expiresOn,
                             Integer contractedSeats, Integer includedReplacements,
                             Integer additionalReplacements, Integer standardReleaseHours,
                             Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
                             LocalDate cycleEndsOn, Long version) {
            this(name, contentMode, expiresOn, contractedSeats, includedReplacements,
                    additionalReplacements, standardReleaseHours, exhaustedReleaseDays,
                    cycleStartsOn, cycleEndsOn, version, false);
        }

        public UpdateCommand(String name, ContentMode contentMode, LocalDate ignoredValidFrom, LocalDate expiresOn,
                             Integer contractedSeats, Integer includedReplacements,
                             Integer additionalReplacements, Integer standardReleaseHours,
                             Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
                             LocalDate cycleEndsOn, Long version) {
            this(name, contentMode, expiresOn, contractedSeats, includedReplacements,
                    additionalReplacements, standardReleaseHours, exhaustedReleaseDays,
                    cycleStartsOn, cycleEndsOn, version, false);
        }
    }
}
