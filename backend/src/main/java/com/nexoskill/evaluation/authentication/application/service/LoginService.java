package com.nexoskill.evaluation.authentication.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.model.CurrentUser;
import com.nexoskill.evaluation.authentication.application.model.LoginCommand;
import com.nexoskill.evaluation.authentication.application.model.LoginResult;
import com.nexoskill.evaluation.authentication.application.port.out.LoginAttemptPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.port.out.SessionTokenGenerator;
import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.domain.AuthenticationException;
import com.nexoskill.evaluation.authentication.domain.model.AuthSession;
import com.nexoskill.evaluation.authentication.domain.model.AuthSessionScope;
import com.nexoskill.evaluation.authentication.domain.repository.AuthSessionRepository;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.UserOrganizationMembershipRepository;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final UserOrganizationMembershipRepository membershipRepository;
    private final PasswordHasher passwordHasher;
    private final SessionTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final LoginAttemptPort loginAttemptPort;
    private final AuditLogPort auditLogPort;
    private final AppProperties properties;
    private final Clock clock;

    public LoginService(UserRepository userRepository, AuthSessionRepository sessionRepository,
            UserOrganizationMembershipRepository membershipRepository, PasswordHasher passwordHasher,
            SessionTokenGenerator tokenGenerator, TokenHasher tokenHasher, LoginAttemptPort loginAttemptPort,
            AuditLogPort auditLogPort, AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.membershipRepository = membershipRepository;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.loginAttemptPort = loginAttemptPort;
        this.auditLogPort = auditLogPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResult login(LoginCommand command) {
        Instant now = clock.instant();
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        UserAccount user = userRepository.findByNormalizedEmail(normalizedEmail).orElseThrow(() -> {
            recordFailure(null, command, "INVALID_CREDENTIALS", now);
            return AuthenticationException.invalidCredentials();
        });

        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            user.registerFailedLogin(properties.getSecurity().getMaxFailedAttempts(), now,
                    properties.getSecurity().getLockDuration());
            userRepository.save(user);
            recordFailure(user.getId(), command, "INVALID_CREDENTIALS", now);
            throw AuthenticationException.invalidCredentials();
        }

        validateAccountState(user, command, now);
        validateOrganization(user, command, now);

        user.registerSuccessfulLogin(now);
        userRepository.save(user);

        String rawToken = tokenGenerator.generate();
        Instant expiresAt = user.getAccess().capSessionExpiration(now.plus(properties.getSecurity().getSessionDuration()));
        expiresAt = user.capSessionExpirationForPassword(expiresAt);
        AuthSessionScope scope = user.isPasswordChangeRequired()
                ? AuthSessionScope.PASSWORD_CHANGE
                : AuthSessionScope.FULL;

        sessionRepository.save(AuthSession.create(UUID.randomUUID().toString(), user.getId(),
                tokenHasher.hash(rawToken), scope, command.ipAddress(), command.userAgent(), now, expiresAt));
        loginAttemptPort.record(user.getId(), command.email(), true, null, command.ipAddress(), command.userAgent(), now);
        auditLogPort.record(user.getId(), user.isPasswordChangeRequired() ? "TEMPORARY_LOGIN_SUCCEEDED" : "LOGIN_SUCCEEDED",
                "AUTHENTICATION", user.isPasswordChangeRequired()
                        ? "El usuario inició una sesión restringida para cambiar su contraseña."
                        : "El usuario inició sesión.",
                command.ipAddress(), command.userAgent(), Map.of("sessionScope", scope.name()), now);
        return new LoginResult(rawToken, expiresAt, CurrentUser.from(user, now));
    }

    private void validateAccountState(UserAccount user, LoginCommand command, Instant now) {
        if (user.getStatus() == UserStatus.DELETED) {
            recordFailure(user.getId(), command, "ACCOUNT_DELETED", now);
            throw AuthenticationException.accountDeleted();
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            recordFailure(user.getId(), command, "ACCOUNT_INACTIVE", now);
            throw AuthenticationException.accountInactive();
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            recordFailure(user.getId(), command, "ACCOUNT_SUSPENDED", now);
            throw AuthenticationException.accountSuspended();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            recordFailure(user.getId(), command, "ACCOUNT_TEMPORARILY_LOCKED", now);
            throw AuthenticationException.accountTemporarilyLocked();
        }
        if (user.isTemporaryPasswordExpiredAt(now)) {
            recordFailure(user.getId(), command, "TEMP_PASSWORD_EXPIRED", now);
            throw AuthenticationException.temporaryPasswordExpired();
        }
        UserAccessStatus accessStatus = user.getAccess().effectiveStatusAt(now);
        if (accessStatus == UserAccessStatus.EXPIRED) {
            recordFailure(user.getId(), command, "ACCESS_EXPIRED", now);
            throw AuthenticationException.accessExpired();
        }
        if (!user.canAuthenticateAt(now)) {
            recordFailure(user.getId(), command, "ACCOUNT_UNAVAILABLE", now);
            throw AuthenticationException.accountInactive();
        }
    }

    private void validateOrganization(UserAccount user, LoginCommand command, Instant now) {
        if (!user.hasRole("MANAGER") && !user.hasRole("SUPERVISOR")) {
            return;
        }
        var organization = membershipRepository.findActiveOrganizationForUser(user.getId()).orElseThrow(() -> {
            recordFailure(user.getId(), command, "ORGANIZATION_INACTIVE", now);
            return AuthenticationException.organizationInactive();
        });
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (organization.getStatus() == OrganizationStatus.EXPIRED
                || (organization.getExpiresOn() != null && today.isAfter(organization.getExpiresOn()))) {
            recordFailure(user.getId(), command, "ORGANIZATION_EXPIRED", now);
            throw AuthenticationException.organizationExpired();
        }
        if (organization.getStatus() != OrganizationStatus.ACTIVE
                || (organization.getValidFrom() != null && today.isBefore(organization.getValidFrom()))) {
            recordFailure(user.getId(), command, "ORGANIZATION_INACTIVE", now);
            throw AuthenticationException.organizationInactive();
        }
    }

    private void recordFailure(Long userId, LoginCommand command, String reason, Instant now) {
        loginAttemptPort.record(userId, command.email(), false, reason, command.ipAddress(), command.userAgent(), now);
        auditLogPort.record(userId, "LOGIN_FAILED", "AUTHENTICATION", "Se rechazó un intento de inicio de sesión.",
                command.ipAddress(), command.userAgent(), Map.of("attemptedEmail", command.email(), "reason", reason), now);
    }
}
