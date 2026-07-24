package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
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
    private final PasswordPolicy passwordPolicy;
    private final AuditLogPort auditLogPort;
    private final AppProperties properties;
    private final Clock clock;

    public CreateUserService(
            UserManagementPort userManagementPort,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            AuditLogPort auditLogPort,
            AppProperties properties,
            Clock clock) {
        this.userManagementPort = userManagementPort;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.auditLogPort = auditLogPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary create(CreateUserCommand command) {
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        validateDates(command.startsAt(), command.expiresAt());
        passwordPolicy.validate(command.temporaryPassword(), command.email());

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
                        command.expiresAt(),
                        clock.instant().plus(
                                properties.getSecurity().getTemporaryPasswordDuration()
                        )
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

    private String normalizedDisplayName(CreateUserCommand command) {
        if (command.displayName() != null && !command.displayName().isBlank()) {
            return command.displayName().trim();
        }
        return command.firstName().trim() + " " + command.lastName().trim();
    }
}
