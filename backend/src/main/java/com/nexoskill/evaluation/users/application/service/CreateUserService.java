package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.application.model.CreateUserCommand;
import com.nexoskill.evaluation.users.application.model.CreateUserResult;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
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
    private final SecureTemporaryPasswordGenerator passwordGenerator;
    private final InternalRolePolicy rolePolicy;
    private final InternalUserStatusHistoryService statusHistoryService;
    private final AuditLogPort auditLogPort;
    private final AppProperties properties;
    private final Clock clock;

    public CreateUserService(UserManagementPort userManagementPort, PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy, SecureTemporaryPasswordGenerator passwordGenerator,
            InternalRolePolicy rolePolicy, InternalUserStatusHistoryService statusHistoryService,
            AuditLogPort auditLogPort, AppProperties properties, Clock clock) {
        this.userManagementPort = userManagementPort;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordGenerator = passwordGenerator;
        this.rolePolicy = rolePolicy;
        this.statusHistoryService = statusHistoryService;
        this.auditLogPort = auditLogPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CreateUserResult create(CreateUserCommand command) {
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        UserManagementSupport.validateDates(command.startsAt(), command.expiresAt());
        String roleCode = rolePolicy.normalizeAndValidate(command.roleCode());
        rolePolicy.validateOrganization(roleCode, command.organizationPublicId());
        UserStatus initialStatus = UserStatus.ACTIVE;
        if (userManagementPort.existsByNormalizedEmail(normalizedEmail)) {
            throw new BusinessException("USER_EMAIL_EXISTS", "Ya existe un usuario registrado con ese correo.");
        }

        String temporaryPassword = generateValidTemporaryPassword(command.email());
        Instant now = clock.instant();
        var created = userManagementPort.create(new UserManagementPort.NewUserData(UUID.randomUUID().toString(),
                command.email().trim(), normalizedEmail, passwordHasher.encode(temporaryPassword),
                command.firstName().trim(), command.lastName().trim(),
                UserManagementSupport.displayName(command.firstName(), command.lastName(), command.displayName()),
                roleCode, normalizeOrganization(command.organizationPublicId()), initialStatus, command.startsAt(),
                command.expiresAt(), now.plus(properties.getSecurity().getTemporaryPasswordDuration()),
                command.actorUserId(), now));

        Long createdInternalId = userManagementPort.getByPublicId(created.publicId()).internalId();
        statusHistoryService.record(createdInternalId, command.actorUserId(), null, initialStatus,
                "Creación de usuario", now);

        auditLogPort.record(command.actorUserId(), "USER_CREATED", "USER_MANAGEMENT",
                "Se creó un usuario interno con contraseña temporal de visualización única.", command.ipAddress(),
                command.userAgent(), Map.of("targetUserPublicId", created.publicId(), "targetEmail", created.email(),
                        "role", roleCode, "initialStatus", initialStatus.name()), now);
        auditLogPort.record(command.actorUserId(), "TEMPORARY_PASSWORD_GENERATED", "USER_MANAGEMENT",
                "Se generó una contraseña temporal para un usuario interno.", command.ipAddress(),
                command.userAgent(), Map.of("targetUserPublicId", created.publicId()), now);

        return new CreateUserResult(created, temporaryPassword);
    }

    private String generateValidTemporaryPassword(String email) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = passwordGenerator.generate();
            try {
                passwordPolicy.validate(candidate, email);
                return candidate;
            } catch (BusinessException exception) {
                if (!"PASSWORD_CONTAINS_EMAIL".equals(exception.getCode())) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No fue posible generar una contraseña temporal segura.");
    }

    private String normalizeOrganization(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
