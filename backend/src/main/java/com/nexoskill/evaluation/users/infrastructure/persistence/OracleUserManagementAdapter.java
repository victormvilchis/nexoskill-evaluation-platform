package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class OracleUserManagementAdapter implements UserManagementPort {
    private final SpringDataUserJpaRepository userRepository;
    private final SpringDataRoleJpaRepository roleRepository;
    private final Clock clock;

    public OracleUserManagementAdapter(SpringDataUserJpaRepository userRepository,
            SpringDataRoleJpaRepository roleRepository, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.clock = clock;
    }

    @Override public boolean existsByNormalizedEmail(String email) { return userRepository.existsByNormalizedEmail(email); }
    @Override public boolean existsByNormalizedEmailExcluding(String email, String publicId) {
        return userRepository.existsByNormalizedEmailAndPublicIdNot(email, publicId);
    }
    @Override public AdminUserSummary create(NewUserData data) {
        var entity = UserJpaEntity.create(data.publicId(), data.email(), data.normalizedEmail(), data.passwordHash(),
                data.firstName(), data.lastName(), data.displayName(), activeRole(data.roleCode()), data.startsAt(),
                data.expiresAt(), true, null, data.temporaryPasswordExpiresAt());
        return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserPage search(String query, UserStatus status, int page, int size) {
        Page<UserJpaEntity> result = userRepository.search(query, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayName")));
        return new AdminUserPage(result.getContent().stream().map(this::toSummary).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @Override public ManagedUser getByPublicId(String publicId) {
        var entity = findUser(publicId); return new ManagedUser(entity.getId(), toSummary(entity));
    }
    @Override public AdminUserSummary updateProfile(String publicId, String email, String normalizedEmail,
            String firstName, String lastName, String displayName) {
        var entity = mutableUser(publicId); entity.updateProfile(email, normalizedEmail, firstName, lastName, displayName);
        return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserSummary updateAccess(String publicId, Instant startsAt, Instant expiresAt) {
        var entity = mutableUser(publicId); entity.getAccess().updatePeriod(startsAt, expiresAt, clock.instant());
        return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserSummary updateRole(String publicId, String roleCode) {
        var entity = mutableUser(publicId); entity.replaceRole(activeRole(roleCode));
        return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserSummary updateStatus(String publicId, UserStatus userStatus,
            UserAccessStatus accessStatus, Instant changedAt) {
        var entity = mutableUser(publicId); entity.changeStatus(userStatus);
        entity.getAccess().changeStatus(accessStatus, changedAt); return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserSummary softDelete(String publicId, Long actorUserId, String reason, Instant changedAt) {
        var entity = mutableUser(publicId); entity.softDelete(actorUserId, changedAt, reason);
        entity.getAccess().changeStatus(UserAccessStatus.CANCELED, changedAt);
        return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserSummary restore(String publicId, Instant changedAt) {
        var entity = findUser(publicId);
        if (entity.getStatus() != UserStatus.DELETED) throw new BusinessException("USER_NOT_DELETED",
                "El usuario no se encuentra eliminado.");
        entity.restoreAsSuspended(); entity.getAccess().changeStatus(UserAccessStatus.SUSPENDED, changedAt);
        return toSummary(userRepository.save(entity));
    }
    @Override public AdminUserSummary updatePassword(String publicId, String passwordHash, Instant expiresAt) {
        var entity = mutableUser(publicId); entity.resetPassword(passwordHash, expiresAt);
        return toSummary(userRepository.save(entity));
    }
    @Override public long countEffectiveAdministrators(Instant now) {
        return userRepository.countEffectiveAdministrators(now, UserStatus.ACTIVE, UserAccessStatus.ACTIVE);
    }
    @Override public List<RoleOption> listActiveRoles() {
        return roleRepository.findByStatusOrderByNameAsc("ACTIVE").stream()
                .map(role -> new RoleOption(role.getCode(), role.getName())).toList();
    }
    private UserJpaEntity findUser(String id) { return userRepository.findByPublicId(id).orElseThrow(() ->
            new BusinessException("USER_NOT_FOUND", "El usuario solicitado no existe.")); }
    private UserJpaEntity mutableUser(String id) {
        var entity = findUser(id);
        if (entity.getStatus() == UserStatus.DELETED) throw new BusinessException("USER_DELETED",
                "El usuario está eliminado. Restáuralo antes de modificarlo.");
        return entity;
    }
    private RoleJpaEntity activeRole(String code) { return roleRepository.findByCode(code)
            .filter(role -> "ACTIVE".equals(role.getStatus())).orElseThrow(() ->
                    new BusinessException("ROLE_NOT_FOUND", "El rol seleccionado no existe o está inactivo.")); }
    private AdminUserSummary toSummary(UserJpaEntity entity) {
        Set<String> roles = entity.getRoles().stream().map(RoleJpaEntity::getCode)
                .collect(Collectors.toUnmodifiableSet());
        var access = entity.getAccess();
        return new AdminUserSummary(entity.getPublicId(), entity.getEmail(), entity.getFirstName(), entity.getLastName(),
                entity.getDisplayName(), entity.getStatus(), roles, access.toDomain().effectiveStatusAt(clock.instant()),
                access.getStartsAt(), access.getExpiresAt(), entity.getLastLoginAt());
    }
}
