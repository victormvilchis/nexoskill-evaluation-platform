package com.nexoskill.evaluation.organizations.application;

import com.nexoskill.evaluation.authentication.infrastructure.persistence.SpringDataAuthSessionRepository;
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
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationSearchRow;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationStatusHistoryJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationStatusHistoryRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
	private final OrganizationStatusHistoryRepository statusHistoryRepository;
	private final SpringDataAuthSessionRepository authSessionRepository;
	private final StudentSessionRepository studentSessionRepository;
	private final StudentRepository studentRepository;
	private final Clock clock;

	@Autowired
	public OrganizationService(OrganizationRepository organizationRepository,
			OrganizationLicensePolicyRepository licenseRepository, AuditLogPort auditLogPort,
			OrganizationCertificationPolicyRepository certificationPolicyRepository,
			OrganizationStatusHistoryRepository statusHistoryRepository,
			SpringDataAuthSessionRepository authSessionRepository, StudentSessionRepository studentSessionRepository,
			StudentRepository studentRepository, Clock clock) {
		this.organizationRepository = organizationRepository;
		this.licenseRepository = licenseRepository;
		this.auditLogPort = auditLogPort;
		this.certificationPolicyRepository = certificationPolicyRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.authSessionRepository = authSessionRepository;
		this.studentSessionRepository = studentSessionRepository;
		this.studentRepository = studentRepository;
		this.clock = clock;
	}

	/** Constructor acotado para las pruebas de dominio ya existentes. */
	public OrganizationService(OrganizationRepository organizationRepository,
			OrganizationLicensePolicyRepository licenseRepository, Clock clock) {
		this(organizationRepository, licenseRepository,
				(userId, eventType, moduleCode, description, ipAddress, userAgent, eventData, occurredAt) -> {
				}, null, null, null, null, null, clock);
	}

	@Transactional(readOnly = true)
	public Page<OrganizationListItem> search(String query, OrganizationStatus status, int page, int size) {
		String normalized = query == null || query.isBlank() ? null : query.trim();
		Page<OrganizationSearchRow> result = organizationRepository.search(normalized, status, LocalDate.now(clock),
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name").ascending()));
		return result.map(row -> new OrganizationListItem(row.getOrganization(), row.getStudentCount(),
				row.getActiveStudentCount(), row.getInactiveStudentCount(), row.getExpiredStudentCount()));
	}

	@Transactional(readOnly = true)
	public OrganizationAggregate get(String publicId) {
		String normalizedPublicId = PublicIdNormalizer.requiredUuid(publicId, "ORGANIZATION_NOT_FOUND",
				"La organización solicitada no existe.");
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
		long studentCount = 0;
		long activeStudentCount = 0;
		long inactiveStudentCount = 0;
		long expiredStudentCount = 0;
		if (studentRepository != null) {
			studentCount = studentRepository.countByOrganizationIdAndStatusNot(organization.getId(),
					StudentStatus.DELETED);
			activeStudentCount = studentRepository.countByOrganizationIdAndStatusNotAndAdmissionDateIsNotNull(
					organization.getId(), StudentStatus.DELETED);
			inactiveStudentCount = studentRepository.countByOrganizationIdAndStatusNotAndAdmissionDateIsNull(
					organization.getId(), StudentStatus.DELETED);
			expiredStudentCount = organization.isAppliesCertifications()
					? studentRepository.countExpiredByOrganizationId(organization.getId(), LocalDate.now(clock))
					: 0;
		}
		return new OrganizationAggregate(organization, policy, studentCount, activeStudentCount, inactiveStudentCount,
				expiredStudentCount);
	}

	@Transactional
	public OrganizationAggregate create(CreateCommand command) {
		String code = normalizeCode(command.code());
		String name = required(command.name(), "nombre");
		if (OrganizationJpaEntity.GLOBAL_CODE.equals(code)) {
			throw error("GLOBAL_ORGANIZATION_RESERVED", "El código GLOBAL está reservado por el sistema.");
		}
		if (organizationRepository.existsByCode(code) || organizationRepository.existsByNameIgnoreCase(name)) {
			throw error("ORGANIZATION_DUPLICATE", "Ya existe una organización con el mismo nombre o código.");
		}

		ContentMode contentMode = requiredContentMode(command.contentMode());
		LocalDate validFrom = LocalDate.now(clock);
		validateExpiration(validFrom, command.expiresOn());
		LicensePolicy policy = createPolicy(command, validFrom);
		Instant now = clock.instant();
		Long actorId = actorId();

		try {
			OrganizationJpaEntity organizationToCreate = OrganizationJpaEntity.createCustomer(
					UUID.randomUUID().toString(), code, name, contentMode, validFrom, command.expiresOn(), actorId,
					now);
			boolean appliesCertifications = Boolean.TRUE.equals(command.appliesCertifications());
			organizationToCreate.configureCertifications(appliesCertifications, actorId, now);
			organizationToCreate.configureStudentCode(Boolean.TRUE.equals(command.manualStudentCode()), actorId, now);

			OrganizationJpaEntity organization = organizationRepository.saveAndFlush(organizationToCreate);
			if (organization == null || organization.getId() == null) {
				throw error("ORGANIZATION_PERSISTENCE_INVALID",
						"No fue posible crear la organización debido a una inconsistencia de persistencia.");
			}

			OrganizationLicensePolicyJpaEntity license = licenseRepository
					.saveAndFlush(OrganizationLicensePolicyJpaEntity.create(organization.getId(),
							policy.contractedSeats(), policy.includedReplacements(), policy.additionalReplacements(),
							policy.standardReleaseHours(), policy.exhaustedReplacementReleaseDays(),
							policy.cycleStartsOn(), policy.cycleEndsOn(), now));
			if (license == null || license.getId() == null
					|| !Objects.equals(organization.getId(), license.getOrganizationId())) {
				throw error("ORGANIZATION_LICENSE_PERSISTENCE_INVALID",
						"La configuración de licenciamiento no es válida.");
			}

			// No se generan registros de certificación vacíos cuando la organización no los
			// utiliza.
			if (appliesCertifications) {
				initializeCertificationPolicies(organization.getId(), actorId, now);
				auditCertificationSetting(organization, false, true, actorId, now);
			}
			auditOrganizationEvent("ORGANIZATION_CREATED", organization, "Se creó la organización comercial.", null,
					actorId, now);
			return new OrganizationAggregate(organization, license, 0, 0, 0, 0);
		} catch (DataIntegrityViolationException exception) {
			throw translatePersistenceFailure(exception);
		}
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
		if (organizationRepository.existsByNameIgnoreCaseAndIdNot(name, aggregate.organization().getId())) {
			throw error("ORGANIZATION_DUPLICATE", "Ya existe una organización con el mismo nombre o código.");
		}
		ContentMode contentMode = requiredContentMode(command.contentMode());
		validateExpiration(aggregate.organization().getValidFrom(), command.expiresOn());
		LicensePolicy policy = updatePolicy(command);
		Instant now = clock.instant();
		Long actorId = actorId();
		boolean previousCertificationSetting = aggregate.organization().isAppliesCertifications();
		boolean requestedCertificationSetting = command.appliesCertifications() == null ? previousCertificationSetting
				: Boolean.TRUE.equals(command.appliesCertifications());

		try {
			aggregate.organization().updateCustomer(name, contentMode, command.expiresOn(), actorId, now);
			aggregate.organization().configureCertifications(requestedCertificationSetting, actorId, now);
			aggregate.organization().configureStudentCode(Boolean.TRUE.equals(command.manualStudentCode()), actorId,
					now);
			aggregate.policy().update(policy.contractedSeats(), policy.includedReplacements(),
					policy.additionalReplacements(), policy.standardReleaseHours(),
					policy.exhaustedReplacementReleaseDays(), policy.cycleStartsOn(), policy.cycleEndsOn(), now);
			organizationRepository.flush();
			licenseRepository.flush();
			if (!previousCertificationSetting && requestedCertificationSetting) {
				initializeCertificationPolicies(aggregate.organization().getId(), actorId, now);
			}
			if (previousCertificationSetting != requestedCertificationSetting) {
				auditCertificationSetting(aggregate.organization(), previousCertificationSetting,
						requestedCertificationSetting, actorId, now);
			}
			auditOrganizationEvent("ORGANIZATION_UPDATED", aggregate.organization(),
					"Se actualizaron los datos operativos de la organización.", null, actorId, now);
			return aggregate;
		} catch (DataIntegrityViolationException exception) {
			throw translatePersistenceFailure(exception);
		}
	}

	@Transactional
	public OrganizationAggregate activate(String publicId, String reason) {
		OrganizationAggregate aggregate = get(publicId);
		ensureTransition(aggregate.organization(), OrganizationStatus.ACTIVE);
		return transition(aggregate, OrganizationStatus.ACTIVE, reason, false);
	}

	@Transactional
	public OrganizationAggregate deactivate(String publicId, String reason) {
		OrganizationAggregate aggregate = get(publicId);
		if (aggregate.organization().getStatus() == OrganizationStatus.DELETED) {
			throw error("ORGANIZATION_RESTORE_REQUIRED", "Restaura la organización antes de administrarla nuevamente.");
		}
		ensureTransition(aggregate.organization(), OrganizationStatus.INACTIVE);
		return transition(aggregate, OrganizationStatus.INACTIVE, reason, true);
	}

	@Transactional
	public OrganizationAggregate softDelete(String publicId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw error("ORGANIZATION_DELETE_REASON_REQUIRED",
					"Indica el motivo de la eliminación lógica de la organización.");
		}
		OrganizationAggregate aggregate = get(publicId);
		ensureTransition(aggregate.organization(), OrganizationStatus.DELETED);
		return transition(aggregate, OrganizationStatus.DELETED, reason, true);
	}

	@Transactional
	public OrganizationAggregate restore(String publicId, String reason) {
		OrganizationAggregate aggregate = get(publicId);
		ensureTransition(aggregate.organization(), OrganizationStatus.INACTIVE);
		if (aggregate.organization().getStatus() != OrganizationStatus.DELETED) {
			throw error("ORGANIZATION_RESTORE_INVALID",
					"Solo una organización eliminada lógicamente puede restaurarse.");
		}
		return transition(aggregate, OrganizationStatus.INACTIVE, reason, true);
	}

	/** Compatibilidad controlada con el endpoint anterior. */
	@Transactional
	public OrganizationAggregate changeStatus(String publicId, OrganizationStatus status) {
		if (status == OrganizationStatus.ACTIVE)
			return activate(publicId, null);
		if (status == OrganizationStatus.INACTIVE)
			return deactivate(publicId, null);
		if (status == OrganizationStatus.DELETED)
			return softDelete(publicId, "Eliminación administrativa");
		throw error("ORGANIZATION_STATUS_INVALID",
				"Utiliza las acciones Activar, Desactivar o Eliminar para administrar la organización.");
	}

	@Transactional(readOnly = true)
	public List<StatusHistoryItem> statusHistory(String publicId) {
		OrganizationAggregate aggregate = get(publicId);
		if (statusHistoryRepository == null)
			return List.of();
		return statusHistoryRepository.findByOrganizationIdOrderByChangedAtDesc(aggregate.organization().getId())
				.stream().map(row -> new StatusHistoryItem(row.getPreviousStatus(), row.getNewStatus(), row.getReason(),
						row.getChangedBy(), row.getChangedAt()))
				.toList();
	}

	private OrganizationAggregate transition(OrganizationAggregate aggregate, OrganizationStatus next, String reason,
			boolean revokeSessions) {
		OrganizationJpaEntity organization = aggregate.organization();
		OrganizationStatus previous = organization.getStatus();
		Long actorId = actorId();
		Instant now = clock.instant();
		organization.transitionTo(next, reason, actorId, now);
		organizationRepository.flush();
		if (statusHistoryRepository != null) {
			statusHistoryRepository.save(OrganizationStatusHistoryJpaEntity.create(organization.getId(), previous, next,
					reason, actorId, now));
		}
		if (revokeSessions)
			revokeOrganizationSessions(organization.getId(), now);
		auditOrganizationEvent("ORGANIZATION_STATUS_CHANGED", organization,
				"La organización cambió de " + previous + " a " + next + ".", reason, actorId, now);
		return aggregate;
	}

	private void ensureTransition(OrganizationJpaEntity organization, OrganizationStatus next) {
		ensureCustomer(organization);
		OrganizationStatus current = organization.getStatus();
		if (current == next) {
			throw error("ORGANIZATION_STATUS_UNCHANGED", "La organización ya se encuentra en ese estado.");
		}
		boolean valid = switch (next) {
		case INACTIVE -> current == OrganizationStatus.ACTIVE || current == OrganizationStatus.SUSPENDED
				|| current == OrganizationStatus.EXPIRED || current == OrganizationStatus.DELETED;
		case ACTIVE -> current == OrganizationStatus.INACTIVE;
		case DELETED -> current == OrganizationStatus.INACTIVE;
		default -> false;
		};
		if (!valid) {
			if (current == OrganizationStatus.ACTIVE && next == OrganizationStatus.DELETED) {
				throw error("ORGANIZATION_MUST_BE_INACTIVE",
						"Desactiva la organización antes de eliminarla lógicamente.");
			}
			throw error("ORGANIZATION_TRANSITION_INVALID",
					"La transición de " + current + " a " + next + " no está permitida.");
		}
	}

	private void revokeOrganizationSessions(Long organizationId, Instant now) {
		if (authSessionRepository != null) {
			authSessionRepository.revokeActiveSessionsByOrganization(organizationId, now);
		}
		if (studentSessionRepository != null) {
			studentSessionRepository.revokeActiveByOrganization(organizationId, StudentSessionStatus.ACTIVE,
					StudentSessionStatus.REVOKED, StudentSessionRevocationReason.ORGANIZATION_UNAVAILABLE, now);
		}
	}

	private void initializeCertificationPolicies(Long organizationId, Long actorId, Instant now) {
		if (certificationPolicyRepository == null)
			return;
		for (CertificationType type : CertificationType.values()) {
			if (certificationPolicyRepository.findByOrganizationIdAndCertificationType(organizationId, type)
					.isPresent()) {
				continue;
			}
			Integer months = type == CertificationType.DEVELOPMENT_SECURITY
					|| type == CertificationType.NORMATIVE_TESTING ? 2 : null;
			Integer days = type == CertificationType.DEVELOPMENT_SECURITY ? 15
					: type == CertificationType.NORMATIVE_TESTING ? 0 : null;
			certificationPolicyRepository.save(
					OrganizationCertificationPolicyJpaEntity.create(organizationId, type, months, days, actorId, now));
		}
		certificationPolicyRepository.flush();
	}

	private void auditCertificationSetting(OrganizationJpaEntity organization, boolean previous, boolean current,
			Long actorId, Instant now) {
		HashMap<String, Object> data = new HashMap<>();
		data.put("organizationPublicId", organization.getPublicId());
		data.put("previousAppliesCertifications", previous);
		data.put("appliesCertifications", current);
		auditLogPort.record(actorId,
				current ? "ORGANIZATION_CERTIFICATIONS_ENABLED" : "ORGANIZATION_CERTIFICATIONS_DISABLED",
				"ORGANIZATIONS", current ? "Se habilitó la gestión de certificaciones para la organización."
						: "Se deshabilitó la gestión de certificaciones para la organización.",
				null, null, data, now);
	}

	private void auditOrganizationEvent(String type, OrganizationJpaEntity organization, String description,
			String reason, Long actorId, Instant now) {
		HashMap<String, Object> data = new HashMap<>();
		data.put("organizationPublicId", organization.getPublicId());
		data.put("organizationCode", organization.getCode());
		data.put("status", organization.getStatus().name());
		if (reason != null && !reason.isBlank())
			data.put("reason", reason.trim());
		auditLogPort.record(actorId, type, "ORGANIZATIONS", description, null, null, data, now);
	}

	private void ensureCustomer(OrganizationJpaEntity organization) {
		if (organization.getOrganizationType() == OrganizationType.GLOBAL) {
			throw error("GLOBAL_ORGANIZATION_IMMUTABLE",
					"La organización global no puede editarse, desactivarse ni eliminarse.");
		}
	}

	private BusinessException translatePersistenceFailure(DataIntegrityViolationException exception) {
		String message = rootMessage(exception).toUpperCase(Locale.ROOT);
		if (message.contains("UK_ORGANIZATION_CODE") || message.contains("ORGANIZATION_NAME")) {
			return error("ORGANIZATION_DUPLICATE", "Ya existe una organización con el mismo nombre o código.");
		}
		if (message.contains("CK_ORGANIZATION_DATES")) {
			return error("ORGANIZATION_VALIDITY_INVALID",
					"La fecha de vencimiento debe ser posterior a la fecha de creación.");
		}
		if (message.contains("ORG_LICENSE") || message.contains("LICENSE_POLICY")) {
			return error("ORGANIZATION_LICENSE_POLICY_INVALID", "La configuración de licenciamiento no es válida.");
		}
		return error("ORGANIZATION_CREATE_FAILED", "No fue posible crear la organización debido a un error interno.");
	}

	private String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null)
			current = current.getCause();
		return current.getMessage() == null ? "" : current.getMessage();
	}

	private LicensePolicy createPolicy(CreateCommand command, LocalDate organizationStart) {
		int seats = requiredNonNegative(command.contractedSeats(), "Los asientos contratados");
		int included = command.includedReplacements() == null ? LicensePolicy.recommendedIncludedReplacements(seats)
				: nonNegative(command.includedReplacements(), "Las sustituciones incluidas");
		int additional = command.additionalReplacements() == null ? 0
				: nonNegative(command.additionalReplacements(), "Las sustituciones adicionales");
		int releaseHours = command.standardReleaseHours() == null ? 24
				: atLeast(command.standardReleaseHours(), 1, "La liberación estándar");
		int exhaustedDays = command.exhaustedReleaseDays() == null ? 7
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
		return buildPolicy(seats, included, additional, releaseHours, exhaustedDays, command.cycleStartsOn(),
				command.cycleEndsOn());
	}

	private LicensePolicy buildPolicy(int seats, int included, int additional, int releaseHours, int exhaustedDays,
			LocalDate cycleStart, LocalDate cycleEnd) {
		try {
			return new LicensePolicy(seats, included, additional, releaseHours, exhaustedDays, cycleStart, cycleEnd);
		} catch (IllegalArgumentException exception) {
			throw error("ORGANIZATION_LICENSE_POLICY_INVALID", exception.getMessage());
		}
	}

	private Long actorId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null)
			return null;
		Object principal = authentication.getPrincipal();
		return principal instanceof AuthenticatedUser user ? user.internalId() : null;
	}

	private static void validateExpiration(LocalDate validFrom, LocalDate expiresOn) {
		if (expiresOn != null && !expiresOn.isAfter(validFrom)) {
			throw error("ORGANIZATION_VALIDITY_INVALID",
					"La fecha de vencimiento debe ser posterior a la fecha de creación.");
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
			throw error("ORGANIZATION_LICENSE_FIELD_REQUIRED", "Debes indicar " + field.toLowerCase(Locale.ROOT) + ".");
		}
		return nonNegative(value, field);
	}

	private static int requiredAtLeast(Integer value, int minimum, String field) {
		if (value == null) {
			throw error("ORGANIZATION_LICENSE_FIELD_REQUIRED", "Debes indicar " + field.toLowerCase(Locale.ROOT) + ".");
		}
		return atLeast(value, minimum, field);
	}

	private static int nonNegative(int value, String field) {
		return atLeast(value, 0, field);
	}

	private static int atLeast(int value, int minimum, String field) {
		if (value < minimum) {
			throw error("ORGANIZATION_LICENSE_VALUE_INVALID", field + " debe ser mayor o igual a " + minimum + ".");
		}
		return value;
	}

	private static String normalizeCode(String code) {
		String normalized = required(code, "código").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_")
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

	public record OrganizationAggregate(OrganizationJpaEntity organization, OrganizationLicensePolicyJpaEntity policy,
			long studentCount, long activeStudentCount, long inactiveStudentCount, long expiredStudentCount) {
		public OrganizationAggregate(OrganizationJpaEntity organization, OrganizationLicensePolicyJpaEntity policy) {
			this(organization, policy, 0, 0, 0, 0);
		}
	}

	public record OrganizationListItem(OrganizationJpaEntity organization, long studentCount, long activeStudentCount,
			long inactiveStudentCount, long expiredStudentCount) {
	}

	public record StatusHistoryItem(OrganizationStatus previousStatus, OrganizationStatus newStatus, String reason,
			Long changedBy, Instant changedAt) {
	}

	public record CreateCommand(String name, String code, ContentMode contentMode, LocalDate expiresOn,
			Integer contractedSeats, Integer includedReplacements, Integer additionalReplacements,
			Integer standardReleaseHours, Integer exhaustedReleaseDays, LocalDate cycleStartsOn, LocalDate cycleEndsOn,
			Boolean appliesCertifications, Boolean manualStudentCode) {
		public CreateCommand(String name, String code, ContentMode contentMode, LocalDate expiresOn,
				Integer contractedSeats, Integer includedReplacements, Integer additionalReplacements,
				Integer standardReleaseHours, Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
				LocalDate cycleEndsOn) {
			this(name, code, contentMode, expiresOn, contractedSeats, includedReplacements, additionalReplacements,
					standardReleaseHours, exhaustedReleaseDays, cycleStartsOn, cycleEndsOn, false, false);
		}

		public CreateCommand(String name, String code, ContentMode contentMode, LocalDate ignoredValidFrom,
				LocalDate expiresOn, Integer contractedSeats, Integer includedReplacements,
				Integer additionalReplacements, Integer standardReleaseHours, Integer exhaustedReleaseDays,
				LocalDate cycleStartsOn, LocalDate cycleEndsOn) {
			this(name, code, contentMode, expiresOn, contractedSeats, includedReplacements, additionalReplacements,
					standardReleaseHours, exhaustedReleaseDays, cycleStartsOn, cycleEndsOn, false, false);
		}
	}

	public record UpdateCommand(String name, ContentMode contentMode, LocalDate expiresOn, Integer contractedSeats,
			Integer includedReplacements, Integer additionalReplacements, Integer standardReleaseHours,
			Integer exhaustedReleaseDays, LocalDate cycleStartsOn, LocalDate cycleEndsOn, Long version,
			Boolean appliesCertifications, Boolean manualStudentCode) {
		public UpdateCommand(String name, ContentMode contentMode, LocalDate expiresOn, Integer contractedSeats,
				Integer includedReplacements, Integer additionalReplacements, Integer standardReleaseHours,
				Integer exhaustedReleaseDays, LocalDate cycleStartsOn, LocalDate cycleEndsOn, Long version) {
			this(name, contentMode, expiresOn, contractedSeats, includedReplacements, additionalReplacements,
					standardReleaseHours, exhaustedReleaseDays, cycleStartsOn, cycleEndsOn, version, false, false);
		}

		public UpdateCommand(String name, ContentMode contentMode, LocalDate ignoredValidFrom, LocalDate expiresOn,
				Integer contractedSeats, Integer includedReplacements, Integer additionalReplacements,
				Integer standardReleaseHours, Integer exhaustedReleaseDays, LocalDate cycleStartsOn,
				LocalDate cycleEndsOn, Long version) {
			this(name, contentMode, expiresOn, contractedSeats, includedReplacements, additionalReplacements,
					standardReleaseHours, exhaustedReleaseDays, cycleStartsOn, cycleEndsOn, version, false, false);
		}
	}
}
