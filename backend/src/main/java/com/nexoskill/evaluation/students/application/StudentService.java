package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import com.nexoskill.evaluation.users.application.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
	private final StudentRepository studentRepository;
	private final StudentSessionRepository sessionRepository;
	private final OrganizationRepository organizationRepository;
	private final PasswordHasher passwordHasher;
	private final PasswordPolicy passwordPolicy;
	private final AppProperties properties;
	private final AuditLogPort auditLogPort;
	private final Clock clock;

	public StudentService(StudentRepository studentRepository, StudentSessionRepository sessionRepository,
			OrganizationRepository organizationRepository, PasswordHasher passwordHasher, PasswordPolicy passwordPolicy,
			AppProperties properties, AuditLogPort auditLogPort, Clock clock) {
		this.studentRepository = studentRepository;
		this.sessionRepository = sessionRepository;
		this.organizationRepository = organizationRepository;
		this.passwordHasher = passwordHasher;
		this.passwordPolicy = passwordPolicy;
		this.properties = properties;
		this.auditLogPort = auditLogPort;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResult search(TenantContext tenant, String query, StudentEffectiveStatus status, boolean includeDeleted,
			int page, int size) {
		Long organizationId = requireOrganization(tenant);
		int safeSize = Math.min(Math.max(size, 1), 100);
		Page<StudentJpaEntity> result = studentRepository.search(organizationId, normalizeQuery(query), status,
				includeDeleted, clock.instant(),
				PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
		Instant now = clock.instant();
		return new PageResult(result.getContent().stream().map(entity -> summary(entity, now)).toList(),
				result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public StudentDetail get(TenantContext tenant, String publicId) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		return detail(student, clock.instant());
	}

	@Transactional
	public StudentDetail create(TenantContext tenant, CreateCommand command, Actor actor) {
		Long organizationId = requireOrganization(tenant);
		validateDates(command.validFrom(), command.expiresAt());
		if (command.status() != StudentStatus.ACTIVE && command.status() != StudentStatus.INACTIVE) {
			throw new BusinessException("STUDENT_INITIAL_STATUS_INVALID",
					"El estudiante debe crearse como activo o inactivo.");
		}
		Instant now = clock.instant();
		if (command.status() == StudentStatus.ACTIVE && command.expiresAt() != null
				&& !command.expiresAt().isAfter(now)) {
			throw new BusinessException("STUDENT_EXPIRED", "No se puede crear como activo un estudiante vencido.");
		}
		passwordPolicy.validate(command.temporaryPassword(), command.email());
		String normalizedEmail = EmailNormalizer.normalize(command.email());
		String code = normalizeCode(command.studentCode());
		if (studentRepository.existsByOrganizationIdAndNormalizedEmail(organizationId, normalizedEmail)) {
			throw new BusinessException("STUDENT_EMAIL_EXISTS",
					"Ya existe un estudiante con ese correo en la organización.");
		}
		if (studentRepository.existsByOrganizationIdAndStudentCode(organizationId, code)) {
			throw new BusinessException("STUDENT_CODE_EXISTS",
					"Ya existe un estudiante con ese código en la organización.");
		}
		StudentJpaEntity student = StudentJpaEntity.create(UUID.randomUUID().toString(), organizationId, code,
				command.email().trim(), normalizedEmail, passwordHasher.encode(command.temporaryPassword()),
				command.firstName().trim(), command.lastName().trim(),
				displayName(command.displayName(), command.firstName(), command.lastName()), command.status(),
				command.validFrom(), command.expiresAt(),
				now.plus(properties.getSecurity().getTemporaryPasswordDuration()), actor.userId(), now);
		try {
			student = studentRepository.saveAndFlush(student);
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException("STUDENT_CONFLICT",
					"El correo o código del estudiante ya está registrado en la organización.");
		}
		audit(actor, "STUDENT_CREATED", student, Map.of("initialStatus", command.status().name()), now);
		return detail(student, now);
	}

	@Transactional
	public StudentDetail update(TenantContext tenant, String publicId, UpdateCommand command, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		ensureEditable(student);
		validateVersion(student, command.version());
		validateDates(command.validFrom(), command.expiresAt());
		String normalizedEmail = EmailNormalizer.normalize(command.email());
		Long studentId = student.getId();
		studentRepository.findByOrganizationIdAndNormalizedEmail(student.getOrganizationId(), normalizedEmail)
				.filter(existing -> !existing.getId().equals(studentId)).ifPresent(existing -> {
					throw new BusinessException("STUDENT_EMAIL_EXISTS",
							"Ya existe un estudiante con ese correo en la organización.");
				});
		Instant now = clock.instant();
		student.updateProfile(command.email().trim(), normalizedEmail, command.firstName().trim(),
				command.lastName().trim(), displayName(command.displayName(), command.firstName(), command.lastName()),
				command.validFrom(), command.expiresAt(), actor.userId(), now);
		student = studentRepository.save(student);
		if (student.effectiveStatusAt(now) != StudentEffectiveStatus.ACTIVE) {
			revokeSessions(student.getId(), revocationReason(student.effectiveStatusAt(now)), now);
		}
		audit(actor, "STUDENT_UPDATED", student, Map.of(), now);
		return detail(student, now);
	}

	@Transactional
	public StudentDetail activate(TenantContext tenant, String publicId, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		if (student.getStatus() == StudentStatus.DELETED) {
			throw new BusinessException("STUDENT_RESTORE_REQUIRED",
					"Primero restaura al estudiante antes de activarlo.");
		}
		Instant now = clock.instant();
		if (student.getExpiresAt() != null && !student.getExpiresAt().isAfter(now)) {
			throw new BusinessException("STUDENT_EXPIRED", "No se puede activar un estudiante vencido.");
		}
		student.activate(actor.userId(), now);
		studentRepository.save(student);
		audit(actor, "STUDENT_ACTIVATED", student, Map.of(), now);
		return detail(student, now);
	}

	@Transactional
	public StudentDetail deactivate(TenantContext tenant, String publicId, Actor actor) {
		return changeStatus(tenant, publicId, actor, StudentStatus.INACTIVE, StudentSessionRevocationReason.DEACTIVATED,
				"STUDENT_DEACTIVATED");
	}

	@Transactional
	public StudentDetail suspend(TenantContext tenant, String publicId, Actor actor) {
		return changeStatus(tenant, publicId, actor, StudentStatus.SUSPENDED, StudentSessionRevocationReason.SUSPENDED,
				"STUDENT_SUSPENDED");
	}

	@Transactional
	public StudentDetail archive(TenantContext tenant, String publicId, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		if (student.getStatus() == StudentStatus.DELETED) {
			throw new BusinessException("STUDENT_RESTORE_REQUIRED", "El estudiante está eliminado.");
		}
		Instant now = clock.instant();
		student.archive(actor.userId(), now);
		studentRepository.save(student);
		revokeSessions(student.getId(), StudentSessionRevocationReason.ARCHIVED, now);
		audit(actor, "STUDENT_ARCHIVED", student, Map.of(), now);
		return detail(student, now);
	}

	@Transactional
	public StudentDetail delete(TenantContext tenant, String publicId, String reason, Actor actor) {
		if (reason == null || reason.isBlank()) {
			throw new BusinessException("STUDENT_DELETION_REASON_REQUIRED", "El motivo de eliminación es obligatorio.");
		}
		StudentJpaEntity student = findScoped(tenant, publicId);
		if (student.getStatus() == StudentStatus.DELETED) {
			throw new BusinessException("STUDENT_ALREADY_DELETED", "El estudiante ya está eliminado.");
		}
		Instant now = clock.instant();
		student.softDelete(actor.userId(), reason.trim(), now);
		studentRepository.save(student);
		revokeSessions(student.getId(), StudentSessionRevocationReason.DELETED, now);
		audit(actor, "STUDENT_DELETED", student, Map.of("reason", reason.trim()), now);
		return detail(student, now);
	}

	@Transactional
	public StudentDetail restore(TenantContext tenant, String publicId, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		if (student.getStatus() != StudentStatus.DELETED && student.getStatus() != StudentStatus.ARCHIVED) {
			throw new BusinessException("STUDENT_RESTORE_NOT_ALLOWED",
					"Solo se pueden restaurar estudiantes archivados o eliminados.");
		}
		Instant now = clock.instant();
		student.restore(actor.userId(), now);
		studentRepository.save(student);
		audit(actor, "STUDENT_RESTORED", student, Map.of(), now);
		return detail(student, now);
	}

	@Transactional
	public StudentDetail resetPassword(TenantContext tenant, String publicId, String temporaryPassword, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		if (student.getStatus() == StudentStatus.DELETED || student.getStatus() == StudentStatus.ARCHIVED) {
			throw new BusinessException("STUDENT_RESTORE_REQUIRED",
					"Restaura al estudiante antes de restablecer su contraseña.");
		}
		passwordPolicy.validate(temporaryPassword, student.getEmail());
		Instant now = clock.instant();
		student.resetPassword(passwordHasher.encode(temporaryPassword),
				now.plus(properties.getSecurity().getTemporaryPasswordDuration()), actor.userId(), now);
		studentRepository.save(student);
		revokeSessions(student.getId(), StudentSessionRevocationReason.PASSWORD_RESET, now);
		audit(actor, "STUDENT_PASSWORD_RESET", student, Map.of(), now);
		return detail(student, now);
	}

	@Transactional(readOnly = true)
	public List<SessionView> sessions(TenantContext tenant, String publicId) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		return sessionRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream().map(this::sessionView)
				.toList();
	}

	@Transactional
	public void revokeSession(TenantContext tenant, String publicId, String sessionPublicId, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		StudentSessionJpaEntity session = sessionRepository.findByPublicIdAndStudentId(sessionPublicId, student.getId())
				.orElseThrow(() -> new BusinessException("STUDENT_SESSION_NOT_FOUND", "La sesión no existe."));
		Instant now = clock.instant();
		if (session.getStatus() == StudentSessionStatus.ACTIVE) {
			session.revoke(StudentSessionRevocationReason.ADMIN_REVOKED, now);
			sessionRepository.save(session);
		}
		audit(actor, "STUDENT_SESSION_REVOKED", student, Map.of("sessionPublicId", sessionPublicId), now);
	}

	@Transactional
	public void revokeAllSessions(TenantContext tenant, String publicId, Actor actor) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		Instant now = clock.instant();
		revokeSessions(student.getId(), StudentSessionRevocationReason.ADMIN_REVOKED, now);
		audit(actor, "STUDENT_SESSIONS_REVOKED", student, Map.of(), now);
	}

	private StudentDetail changeStatus(TenantContext tenant, String publicId, Actor actor, StudentStatus status,
			StudentSessionRevocationReason reason, String event) {
		StudentJpaEntity student = findScoped(tenant, publicId);
		if (student.getStatus() == StudentStatus.DELETED) {
			throw new BusinessException("STUDENT_RESTORE_REQUIRED", "El estudiante está eliminado.");
		}
		Instant now = clock.instant();
		if (status == StudentStatus.SUSPENDED)
			student.suspend(actor.userId(), now);
		else
			student.deactivate(actor.userId(), now);
		studentRepository.save(student);
		revokeSessions(student.getId(), reason, now);
		audit(actor, event, student, Map.of(), now);
		return detail(student, now);
	}

	private void revokeSessions(Long studentId, StudentSessionRevocationReason reason, Instant now) {
		sessionRepository.revokeActive(studentId, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED, reason,
				now);
	}

	private StudentSessionRevocationReason revocationReason(StudentEffectiveStatus status) {
		return switch (status) {
		case EXPIRED -> StudentSessionRevocationReason.EXPIRED;
		case SUSPENDED -> StudentSessionRevocationReason.SUSPENDED;
		case ARCHIVED -> StudentSessionRevocationReason.ARCHIVED;
		case DELETED -> StudentSessionRevocationReason.DELETED;
		default -> StudentSessionRevocationReason.DEACTIVATED;
		};
	}

	private StudentJpaEntity findScoped(TenantContext tenant, String publicId) {
		return studentRepository.findByOrganizationIdAndPublicId(requireOrganization(tenant), publicId)
				.orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
	}

	private Long requireOrganization(TenantContext tenant) {
		if (tenant == null || !tenant.hasOrganization()) {
			throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED",
					"Selecciona una organización para administrar estudiantes.");
		}
		OrganizationJpaEntity organization = organizationRepository.findById(tenant.organizationId())
				.orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
		return organization.getId();
	}

	private void validateDates(Instant validFrom, Instant expiresAt) {
		if (validFrom == null) {
			throw new BusinessException("STUDENT_VALID_FROM_REQUIRED", "La fecha de inicio es obligatoria.");
		}
		if (expiresAt != null && !expiresAt.isAfter(validFrom)) {
			throw new BusinessException("STUDENT_DATES_INVALID",
					"La fecha de vencimiento debe ser posterior a la fecha de inicio.");
		}
	}

	private void ensureEditable(StudentJpaEntity student) {
		if (student.getStatus() == StudentStatus.DELETED || student.getStatus() == StudentStatus.ARCHIVED) {
			throw new BusinessException("STUDENT_RESTORE_REQUIRED",
					"Restaura al estudiante antes de modificar sus datos.");
		}
	}

	private void validateVersion(StudentJpaEntity student, Long version) {
		if (version == null || !version.equals(student.getVersion())) {
			throw new BusinessException("STUDENT_VERSION_CONFLICT",
					"El estudiante fue modificado por otra operación. Actualiza la página.");
		}
	}

	private String normalizeCode(String code) {
		if (code == null || code.isBlank()) {
			throw new BusinessException("STUDENT_CODE_REQUIRED", "El código del estudiante es obligatorio.");
		}
		String normalized = code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_").replaceAll("_+", "_");
		if (normalized.length() > 80) {
			throw new BusinessException("STUDENT_CODE_INVALID", "El código no puede superar 80 caracteres.");
		}
		return normalized;
	}

	private String normalizeQuery(String query) {
		return query == null || query.isBlank() ? null : query.trim();
	}

	private String displayName(String displayName, String firstName, String lastName) {
		return displayName == null || displayName.isBlank() ? firstName.trim() + " " + lastName.trim()
				: displayName.trim();
	}

	private void audit(Actor actor, String event, StudentJpaEntity student, Map<String, Object> extra, Instant now) {
		java.util.HashMap<String, Object> data = new java.util.HashMap<>(extra);
		data.put("studentPublicId", student.getPublicId());
		data.put("organizationId", student.getOrganizationId());
		auditLogPort.record(actor.userId(), event, "STUDENTS", event, actor.ipAddress(), actor.userAgent(), data, now);
	}

	private StudentSummary summary(StudentJpaEntity student, Instant now) {
		return new StudentSummary(student.getPublicId(), student.getStudentCode(), student.getEmail(),
				student.getDisplayName(), student.getStatus(), student.effectiveStatusAt(now), student.getValidFrom(),
				student.getExpiresAt(), student.getLastLoginAt(), student.getUpdatedAt());
	}

	private StudentDetail detail(StudentJpaEntity student, Instant now) {
		return new StudentDetail(student.getPublicId(), student.getStudentCode(), student.getEmail(),
				student.getFirstName(), student.getLastName(), student.getDisplayName(), student.getStatus(),
				student.effectiveStatusAt(now), student.getValidFrom(), student.getExpiresAt(),
				student.isPasswordChangeRequired(), student.getTemporaryPasswordExpiresAt(), student.getLastLoginAt(),
				student.getArchivedAt(), student.getDeletedAt(), student.getDeletionReason(), student.getCreatedAt(),
				student.getUpdatedAt(), student.getVersion());
	}

	private SessionView sessionView(StudentSessionJpaEntity session) {
		return new SessionView(session.getPublicId(), session.getStatus(), session.getIpAddress(),
				session.getUserAgent(), session.getCreatedAt(), session.getLastActivityAt(), session.getExpiresAt(),
				session.getRevokedAt(),
				session.getRevocationReason() == null ? null : session.getRevocationReason().name());
	}

	public record Actor(Long userId, String ipAddress, String userAgent) {
	}

	public record CreateCommand(String studentCode, String email, String firstName, String lastName, String displayName,
			String temporaryPassword, StudentStatus status, Instant validFrom, Instant expiresAt) {
	}

	public record UpdateCommand(String email, String firstName, String lastName, String displayName, Instant validFrom,
			Instant expiresAt, Long version) {
	}

	public record StudentSummary(String publicId, String studentCode, String email, String displayName,
			StudentStatus status, StudentEffectiveStatus effectiveStatus, Instant validFrom, Instant expiresAt,
			Instant lastLoginAt, Instant updatedAt) {
	}

	public record StudentDetail(String publicId, String studentCode, String email, String firstName, String lastName,
			String displayName, StudentStatus status, StudentEffectiveStatus effectiveStatus, Instant validFrom,
			Instant expiresAt, boolean passwordChangeRequired, Instant temporaryPasswordExpiresAt, Instant lastLoginAt,
			Instant archivedAt, Instant deletedAt, String deletionReason, Instant createdAt, Instant updatedAt,
			Long version) {
	}

	public record SessionView(String publicId, StudentSessionStatus status, String ipAddress, String userAgent,
			Instant createdAt, Instant lastActivityAt, Instant expiresAt, Instant revokedAt, String revocationReason) {
	}

	public record PageResult(List<StudentSummary> content, int page, int size, long totalElements, int totalPages) {
	}
}
