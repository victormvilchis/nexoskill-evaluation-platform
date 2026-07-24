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

    public OracleUserManagementAdapter(
            SpringDataUserJpaRepository userRepository,
            SpringDataRoleJpaRepository roleRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.clock = clock;
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        return userRepository.existsByNormalizedEmail(normalizedEmail);
    }

    @Override
    public boolean existsByNormalizedEmailExcluding(
            String normalizedEmail,
            String excludedPublicId) {
        return userRepository.existsByNormalizedEmailAndPublicIdNot(
                normalizedEmail,
                excludedPublicId
        );
    }

    @Override
    public AdminUserSummary create(NewUserData data) {
        RoleJpaEntity role = activeRole(data.roleCode());

        UserJpaEntity entity = UserJpaEntity.create(
                data.publicId(),
                data.email(),
                data.normalizedEmail(),
                data.passwordHash(),
                data.firstName(),
                data.lastName(),
                data.displayName(),
                role,
                data.startsAt(),
                data.expiresAt()
        );

        return toSummary(userRepository.save(entity));
    }

    @Override
    public AdminUserPage search(
            String query,
            UserStatus status,
            int page,
            int size) {

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "displayName")
        );
        Page<UserJpaEntity> result = userRepository.search(query, status, pageable);

        return new AdminUserPage(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    public ManagedUser getByPublicId(String publicId) {
        UserJpaEntity entity = findUser(publicId);
        return new ManagedUser(entity.getId(), toSummary(entity));
    }

    @Override
    public AdminUserSummary updateProfile(
            String publicId,
            String email,
            String normalizedEmail,
            String firstName,
            String lastName,
            String displayName) {
        UserJpaEntity entity = findUser(publicId);
        entity.updateProfile(
                email,
                normalizedEmail,
                firstName,
                lastName,
                displayName
        );
        return toSummary(userRepository.save(entity));
    }

    @Override
    public AdminUserSummary updateAccess(
            String publicId,
            Instant startsAt,
            Instant expiresAt) {
        UserJpaEntity entity = findUser(publicId);
        entity.getAccess().updatePeriod(startsAt, expiresAt, clock.instant());
        return toSummary(userRepository.save(entity));
    }

    @Override
    public AdminUserSummary updateRole(String publicId, String roleCode) {
        UserJpaEntity entity = findUser(publicId);
        entity.replaceRole(activeRole(roleCode));
        return toSummary(userRepository.save(entity));
    }

    @Override
    public AdminUserSummary updateStatus(
            String publicId,
            UserStatus userStatus,
            UserAccessStatus accessStatus,
            Instant changedAt) {
        UserJpaEntity entity = findUser(publicId);
        entity.changeStatus(userStatus);
        entity.getAccess().changeStatus(accessStatus, changedAt);
        return toSummary(userRepository.save(entity));
    }

    @Override
    public AdminUserSummary updatePassword(
            String publicId,
            String passwordHash) {
        UserJpaEntity entity = findUser(publicId);
        entity.resetPassword(passwordHash);
        return toSummary(userRepository.save(entity));
    }

    @Override
    public List<RoleOption> listActiveRoles() {
        return roleRepository.findByStatusOrderByNameAsc("ACTIVE").stream()
                .map(role -> new RoleOption(role.getCode(), role.getName()))
                .toList();
    }

    private UserJpaEntity findUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "El usuario solicitado no existe."
                ));
    }

    private RoleJpaEntity activeRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(
                        "ROLE_NOT_FOUND",
                        "El rol seleccionado no existe o está inactivo."
                ));
    }

    private AdminUserSummary toSummary(UserJpaEntity entity) {
        Set<String> roles = entity.getRoles().stream()
                .map(RoleJpaEntity::getCode)
                .collect(Collectors.toUnmodifiableSet());

        UserAccessJpaEntity access = entity.getAccess();
        return new AdminUserSummary(
                entity.getPublicId(),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getDisplayName(),
                entity.getStatus(),
                roles,
                access.toDomain().effectiveStatusAt(clock.instant()),
                access.getStartsAt(),
                access.getExpiresAt(),
                entity.getLastLoginAt()
        );
    }
}
