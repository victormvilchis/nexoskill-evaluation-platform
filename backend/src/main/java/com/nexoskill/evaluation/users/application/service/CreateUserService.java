package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.CreateUserCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUserService {

    private final UserManagementPort userManagementPort;
    private final PasswordHasher passwordHasher;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CreateUserService(
            UserManagementPort userManagementPort,
            PasswordHasher passwordHasher,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.userManagementPort = userManagementPort;
        this.passwordHasher = passwordHasher;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary create(CreateUserCommand command) {
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        validateDates(command.startsAt(), command.expiresAt());
        validatePassword(command.temporaryPassword());

        if (userManagementPort.existsByNormalizedEmail(normalizedEmail)) {
            throw new BusinessException(
                    "USER_EMAIL_EXISTS",
                    "Ya existe un usuario registrado con ese correo."
            );
        }

        AdminUserSummary created = userManagementPort.create(
                new UserManagementPort.NewUserData(
                        UUID.randomUUID().toString(),
                        command.email().trim(),
                        normalizedEmail,
                        passwordHasher.encode(command.temporaryPassword()),
                        command.firstName().trim(),
                        command.lastName().trim(),
                        normalizedDisplayName(command),
                        command.roleCode().trim().toUpperCase(),
                        command.startsAt(),
                        command.expiresAt()
                )
        );

        auditLogPort.record(
                command.actorUserId(),
                "USER_CREATED",
                "USER_MANAGEMENT",
                "Se creó un usuario desde el panel administrativo.",
                command.ipAddress(),
                command.userAgent(),
                Map.of(
                        "targetUserPublicId", created.publicId(),
                        "targetEmail", created.email(),
                        "role", command.roleCode().trim().toUpperCase()
                ),
                clock.instant()
        );

        return created;
    }

    private void validateDates(Instant startsAt, Instant expiresAt) {
        if (startsAt == null) {
            throw new BusinessException(
                    "USER_ACCESS_START_REQUIRED",
                    "La fecha de inicio es obligatoria."
            );
        }
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new BusinessException(
                    "USER_ACCESS_DATES_INVALID",
                    "La fecha de vencimiento debe ser posterior a la fecha de inicio."
            );
        }
    }

    private void validatePassword(String password) {
        boolean valid = password != null
                && password.length() >= 10
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(value -> !Character.isLetterOrDigit(value));

        if (!valid) {
            throw new BusinessException(
                    "PASSWORD_POLICY_VIOLATION",
                    "La contraseña temporal debe tener al menos 10 caracteres, mayúscula, minúscula, número y símbolo."
            );
        }
    }

    private String normalizedDisplayName(CreateUserCommand command) {
        if (command.displayName() != null && !command.displayName().isBlank()) {
            return command.displayName().trim();
        }
        return command.firstName().trim() + " " + command.lastName().trim();
    }
}
