package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.domain.model.RoleGrant;
import com.nexoskill.evaluation.users.domain.model.UserAccess;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class OracleUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserJpaRepository repository;
    private final SpringDataPermissionJpaRepository permissionRepository;

    public OracleUserRepositoryAdapter(SpringDataUserJpaRepository repository,
            SpringDataPermissionJpaRepository permissionRepository) {
        this.repository = repository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
        return repository.findByNormalizedEmail(normalizedEmail).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public UserAccount save(UserAccount user) {
        UserJpaEntity entity = repository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        entity.applyAuthenticationState(user.getFailedLoginAttempts(), user.getLockedUntil(), user.getLastLoginAt());
        entity.applyPasswordState(user.getPasswordHash(), user.isPasswordChangeRequired(), user.getPasswordChangedAt(),
                user.getTemporaryPasswordExpiresAt());
        return toDomain(repository.save(entity));
    }

    private UserAccount toDomain(UserJpaEntity entity) {
        Set<RoleJpaEntity> activeRoles = entity.getRoles().stream()
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean administrator = activeRoles.stream().anyMatch(RoleJpaEntity::isProtectedAdministrator);
        Set<String> administratorPermissions = administrator
                ? permissionRepository.findAll().stream().map(PermissionJpaEntity::getCode)
                        .collect(Collectors.toUnmodifiableSet())
                : Set.of();

        Set<RoleGrant> roles = activeRoles.stream()
                .map(role -> new RoleGrant(role.getCode(), role.isProtectedAdministrator()
                        ? administratorPermissions
                        : role.getPermissions().stream().map(PermissionJpaEntity::getCode)
                                .collect(Collectors.toUnmodifiableSet())))
                .collect(Collectors.toUnmodifiableSet());

        return new UserAccount(entity.getId(), entity.getPublicId(), entity.getEmail(), entity.getNormalizedEmail(),
                entity.getPasswordHash(), entity.getFirstName(), entity.getLastName(), entity.getDisplayName(),
                entity.getStatus(), entity.getFailedLoginAttempts(), entity.getLockedUntil(), entity.getLastLoginAt(),
                entity.isPasswordChangeRequired(), entity.getPasswordChangedAt(),
                entity.getTemporaryPasswordExpiresAt(), roles, new UserAccess(entity.getAccess().getStartsAt(),
                        entity.getAccess().getExpiresAt(), entity.getAccess().getStatus()));
    }
}
