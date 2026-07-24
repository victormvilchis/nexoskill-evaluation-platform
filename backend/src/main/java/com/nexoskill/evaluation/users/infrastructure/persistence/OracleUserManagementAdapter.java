package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
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

    public OracleUserManagementAdapter(
            SpringDataUserJpaRepository userRepository,
            SpringDataRoleJpaRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        return userRepository.existsByNormalizedEmail(normalizedEmail);
    }

    @Override
    public AdminUserSummary create(NewUserData data) {
        RoleJpaEntity role = roleRepository.findByCode(data.roleCode())
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(
                        "ROLE_NOT_FOUND",
                        "El rol seleccionado no existe o está inactivo."
                ));

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
    public List<RoleOption> listActiveRoles() {
        return roleRepository.findByStatusOrderByNameAsc("ACTIVE").stream()
                .map(role -> new RoleOption(role.getCode(), role.getName()))
                .toList();
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
                access.getStatus(),
                access.getStartsAt(),
                access.getExpiresAt(),
                entity.getLastLoginAt()
        );
    }
}
