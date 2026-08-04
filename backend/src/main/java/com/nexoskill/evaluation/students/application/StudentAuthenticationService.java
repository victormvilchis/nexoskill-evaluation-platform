package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.port.out.SessionTokenGenerator;
import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import com.nexoskill.evaluation.users.application.service.PasswordPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentAuthenticationService {
	private final OrganizationRepository organizationRepository;
	private final StudentRepository studentRepository;
	private final StudentSessionRepository sessionRepository;
	private final PasswordHasher passwordHasher;
	private final PasswordPolicy passwordPolicy;
	private final SessionTokenGenerator tokenGenerator;
	private final TokenHasher tokenHasher;
	private final AppProperties properties;
	private final AuditLogPort auditLogPort;
	private final Clock clock;

	public StudentAuthenticationService(OrganizationRepository organizationRepository,
			StudentRepository studentRepository, StudentSessionRepository sessionRepository,
			PasswordHasher passwordHasher, PasswordPolicy passwordPolicy, SessionTokenGenerator tokenGenerator,
			TokenHasher tokenHasher, AppProperties properties, AuditLogPort auditLogPort, Clock clock) {
		this.organizationRepository = organizationRepository;
		this.studentRepository = studentRepository;
		this.sessionRepository = sessionRepository;
		this.passwordHasher = passwordHasher;
		this.passwordPolicy = passwordPolicy;
		this.tokenGenerator = tokenGenerator;
		this.tokenHasher = tokenHasher;
		this.properties = properties;
		this.auditLogPort = auditLogPort;
		this.clock = clock;
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public LoginResult login(LoginCommand command) {
		Instant now = clock.instant();
		String organizationCode = command.organizationCode() == null ? ""
				: command.organizationCode().trim().toUpperCase(java.util.Locale.ROOT);
		OrganizationJpaEntity organization = organizationRepository.findByCode(organizationCode)
				.orElseThrow(() -> invalidLogin(command, null, "ORGANIZATION_NOT_FOUND", now));
		if (!organization.isOperational(LocalDate.now(clock))) {
			throw invalidLogin(command, null, "ORGANIZATION_UNAVAILABLE", now);
		}
		String normalizedEmail = EmailNormalizer.normalize(command.email());
		StudentJpaEntity student = studentRepository.findForLogin(organization.getId(), normalizedEmail)
				.orElseThrow(() -> invalidLogin(command, null, "INVALID_CREDENTIALS", now));

		if (!passwordHasher.matches(command.password(), student.getPasswordHash())) {
			student.registerFailedLogin(properties.getSecurity().getMaxFailedAttempts(), now,
					properties.getSecurity().getLockDuration());
			studentRepository.save(student);
			throw invalidLogin(command, student, "INVALID_CREDENTIALS", now);
		}
		if (student.isTemporaryPasswordExpiredAt(now)) {
			throw invalidLogin(command, student, "TEMP_PASSWORD_EXPIRED", now);
		}
		LocalDate today = LocalDate.now(clock);
		if (!student.canAuthenticateOn(today, now)) {
			throw invalidLogin(command, student, "STUDENT_ACCOUNT_UNAVAILABLE", now);
		}

		StudentSessionJpaEntity existing = sessionRepository
				.findByStudentIdAndStatus(student.getId(), StudentSessionStatus.ACTIVE).orElse(null);
		if (existing != null) {
			if (existing.isActiveAt(now)) {
				throw new BusinessException("STUDENT_SESSION_ALREADY_ACTIVE",
						"Ya existe una sesión activa para este estudiante. Cierra esa sesión o solicita su revocación.");
			}
			existing.expire(now);
			sessionRepository.save(existing);
			sessionRepository.flush();
		}

		String rawToken = tokenGenerator.generate();
		Instant expiresAt = now.plus(properties.getSecurity().getSessionDuration());
		if (organization.getExpiresOn() != null) {
			Instant organizationExpiration = organization.getExpiresOn().plusDays(1).atStartOfDay(ZoneOffset.UTC)
					.toInstant();
			if (organizationExpiration.isBefore(expiresAt))
				expiresAt = organizationExpiration;
		}
		if (student.isPasswordChangeRequired() && student.getTemporaryPasswordExpiresAt() != null
				&& student.getTemporaryPasswordExpiresAt().isBefore(expiresAt)) {
			expiresAt = student.getTemporaryPasswordExpiresAt();
		}
		student.registerSuccessfulLogin(now);
		studentRepository.save(student);
		StudentSessionJpaEntity session = StudentSessionJpaEntity.create(UUID.randomUUID().toString(), student.getId(),
				organization.getId(), tokenHasher.hash(rawToken), command.ipAddress(), command.userAgent(), now,
				expiresAt);
		sessionRepository.save(session);
		auditLogPort.record(null, "STUDENT_LOGIN_SUCCEEDED", "STUDENT_AUTHENTICATION", "El estudiante inició sesión.",
				command.ipAddress(), command.userAgent(),
				Map.of("studentPublicId", student.getPublicId(), "organizationPublicId", organization.getPublicId()),
				now);
		return new LoginResult(rawToken, expiresAt, principal(student, organization, now));
	}

	@Transactional
	public void logout(String rawToken, AuthenticatedStudent principal, String ipAddress, String userAgent) {
		Instant now = clock.instant();
		if (rawToken != null && !rawToken.isBlank()) {
			sessionRepository.findByTokenHash(tokenHasher.hash(rawToken)).ifPresent(session -> {
				if (session.getStatus() == StudentSessionStatus.ACTIVE) {
					session.revoke(StudentSessionRevocationReason.LOGOUT, now);
					sessionRepository.save(session);
				}
			});
		}
		auditLogPort.record(null, "STUDENT_LOGOUT_SUCCEEDED", "STUDENT_AUTHENTICATION", "El estudiante cerró sesión.",
				ipAddress, userAgent, Map.of("studentPublicId", principal.publicId()), now);
	}

	@Transactional
	public void changePassword(AuthenticatedStudent principal, String currentPassword, String newPassword,
			String confirmPassword, String ipAddress, String userAgent) {
		if (!java.util.Objects.equals(newPassword, confirmPassword)) {
			throw new BusinessException("PASSWORD_CONFIRMATION_MISMATCH", "La confirmación no coincide.");
		}
		StudentJpaEntity student = studentRepository.findByIdForUpdate(principal.internalId())
				.orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
		if (!passwordHasher.matches(currentPassword, student.getPasswordHash())) {
			throw new BusinessException("CURRENT_PASSWORD_INVALID", "La contraseña actual no es correcta.");
		}
		passwordPolicy.validate(newPassword, student.getEmail());
		if (passwordHasher.matches(newPassword, student.getPasswordHash())) {
			throw new BusinessException("PASSWORD_REUSE_NOT_ALLOWED", "La nueva contraseña debe ser diferente.");
		}
		Instant now = clock.instant();
		student.changePassword(passwordHasher.encode(newPassword), now);
		studentRepository.save(student);
		revokeActive(student.getId(), StudentSessionRevocationReason.PASSWORD_CHANGED, now);
		auditLogPort.record(null, "STUDENT_PASSWORD_CHANGED", "STUDENT_AUTHENTICATION",
				"El estudiante cambió su contraseña.", ipAddress, userAgent,
				Map.of("studentPublicId", student.getPublicId()), now);
	}

	public AuthenticatedStudent principal(StudentJpaEntity student, OrganizationJpaEntity organization, Instant now) {
		return new AuthenticatedStudent(student.getId(), student.getPublicId(), organization.getId(),
				organization.getPublicId(), organization.getCode(), organization.getName(), student.getStudentCode(),
				student.getEmail(), student.getFirstName(), student.getLastName(), student.getDisplayName(),
				student.effectiveStatusOn(LocalDate.now(clock)), organization.getValidFrom(),
				organization.getExpiresOn(), student.getLastLoginAt(), student.isPasswordChangeRequired());
	}

	private BusinessException invalidLogin(LoginCommand command, StudentJpaEntity student, String reason, Instant now) {
		auditLogPort.record(null, "STUDENT_LOGIN_FAILED", "STUDENT_AUTHENTICATION",
				"Se rechazó un inicio de sesión de estudiante.", command.ipAddress(), command.userAgent(),
				Map.of("organizationCode", String.valueOf(command.organizationCode()), "attemptedEmail",
						String.valueOf(command.email()), "reason", reason, "studentPublicId",
						student == null ? "" : student.getPublicId()),
				now);
		return switch (reason) {
		case "TEMP_PASSWORD_EXPIRED" ->
			new BusinessException("STUDENT_TEMP_PASSWORD_EXPIRED", "La contraseña temporal ha expirado.");
		case "STUDENT_ACCESS_EXPIRED" -> new BusinessException(reason, "Tu acceso como estudiante ha vencido.");
		case "STUDENT_ACCOUNT_UNAVAILABLE", "ORGANIZATION_UNAVAILABLE" ->
			new BusinessException("STUDENT_ACCOUNT_UNAVAILABLE", "La cuenta no está disponible.");
		default -> new BusinessException("STUDENT_INVALID_CREDENTIALS", "Las credenciales no son válidas.");
		};
	}

	private void revokeActive(Long studentId, StudentSessionRevocationReason reason, Instant now) {
		sessionRepository.revokeActive(studentId, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED, reason,
				now);
	}

	public record LoginCommand(String organizationCode, String email, String password, String ipAddress,
			String userAgent) {
	}

	public record LoginResult(String rawToken, Instant expiresAt, AuthenticatedStudent student) {
	}
}
