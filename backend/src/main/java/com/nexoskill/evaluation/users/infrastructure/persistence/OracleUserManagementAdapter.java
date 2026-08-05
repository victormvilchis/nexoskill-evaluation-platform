package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.InternalUserOrganizationMembershipRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.UserOrganizationMembershipJpaEntity;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
	private final OrganizationRepository organizationRepository;
	private final InternalUserOrganizationMembershipRepository membershipRepository;
	private final Clock clock;

	public OracleUserManagementAdapter(SpringDataUserJpaRepository userRepository,
			SpringDataRoleJpaRepository roleRepository, OrganizationRepository organizationRepository,
			InternalUserOrganizationMembershipRepository membershipRepository, Clock clock) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.clock = clock;
	}

	@Override
	public boolean existsByNormalizedEmail(String email) {
		return userRepository.existsByNormalizedEmail(email);
	}

	@Override
	public boolean existsByNormalizedEmailExcluding(String email, String publicId) {
		return userRepository.existsByNormalizedEmailAndPublicIdNot(email, publicId);
	}

	@Override
	public AdminUserSummary create(NewUserData data) {
		RoleJpaEntity role = activeRole(data.roleCode());
		validateOrganizationRequirement(data.roleCode(), data.organizationPublicId());
		var entity = UserJpaEntity.create(data.publicId(), data.email(), data.normalizedEmail(), data.passwordHash(),
				data.firstName(), data.lastName(), data.displayName(), role, data.startsAt(), data.expiresAt(),
				data.initialStatus(), true, null, data.temporaryPasswordExpiresAt(), data.actorUserId(),
				data.createdAt());
		entity = userRepository.saveAndFlush(entity);
		assignOrganization(entity.getId(), data.roleCode(), data.organizationPublicId(), data.actorUserId(),
				data.createdAt());
		return toSummary(entity);
	}

	@Override
	public AdminUserPage search(String query, UserStatus status, int page, int size) {
		Page<UserJpaEntity> result = userRepository.search(query, status,
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayName")));
		return new AdminUserPage(result.getContent().stream().map(this::toSummary).toList(), result.getNumber(),
				result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@Override
	public ManagedUser getByPublicId(String publicId) {
		var entity = findInternalUser(publicId);
		return new ManagedUser(entity.getId(), toSummary(entity));
	}

	@Override
	public AdminUserSummary updateProfile(String publicId, String email, String normalizedEmail, String firstName,
			String lastName, String displayName) {
		var entity = mutableUser(publicId);
		entity.updateProfile(email, normalizedEmail, firstName, lastName, displayName);
		return toSummary(userRepository.save(entity));
	}

	@Override
	public AdminUserSummary updateAccess(String publicId, Instant startsAt, Instant expiresAt) {
		var entity = mutableUser(publicId);
		entity.getAccess().updatePeriod(startsAt, expiresAt, clock.instant());
		return toSummary(userRepository.save(entity));
	}

	@Override
	public AdminUserSummary updateRole(String publicId, String roleCode) {
		var entity = mutableUser(publicId);
		entity.replaceRole(activeRole(roleCode));
		return toSummary(userRepository.save(entity));
	}

	@Override
	public AdminUserSummary updateOrganization(String publicId, String organizationPublicId, Long actorUserId,
			Instant changedAt) {
		var entity = mutableUser(publicId);
		String roleCode = entity.getRoles().stream().findFirst().map(RoleJpaEntity::getCode).orElse("");
		validateOrganizationRequirement(roleCode, organizationPublicId);
		assignOrganization(entity.getId(), roleCode, organizationPublicId, actorUserId, changedAt);
		return toSummary(entity);
	}

	@Override
	public AdminUserSummary updateStatus(String publicId, UserStatus userStatus, UserAccessStatus accessStatus,
			Long actorUserId, String reason, Instant changedAt) {
		var entity = mutableUser(publicId);
		entity.changeStatus(userStatus, actorUserId, reason, changedAt);
		entity.getAccess().changeStatus(accessStatus, changedAt);
		return toSummary(userRepository.save(entity));
	}

	@Override
	public AdminUserSummary softDelete(String publicId, Long actorUserId, String reason, Instant changedAt) {
		var entity = mutableUser(publicId);
		entity.softDelete(actorUserId, changedAt, reason);
		entity.getAccess().changeStatus(UserAccessStatus.CANCELED, changedAt);
		return toSummary(userRepository.save(entity));
	}

	@Override
	public AdminUserSummary restore(String publicId, Long actorUserId, String reason, Instant changedAt) {
		var entity = findInternalUser(publicId);
		if (entity.getStatus() != UserStatus.DELETED) {
			throw new BusinessException("USER_NOT_DELETED", "El usuario no se encuentra eliminado.");
		}
		entity.restoreAsInactive(actorUserId, changedAt, reason);
		entity.getAccess().changeStatus(UserAccessStatus.SUSPENDED, changedAt);
		return toSummary(userRepository.save(entity));
	}

	@Override
	public AdminUserSummary updatePassword(String publicId, String passwordHash, Instant expiresAt) {
		var entity = mutableUser(publicId);
		entity.resetPassword(passwordHash, expiresAt);
		return toSummary(userRepository.save(entity));
	}

	@Override
	public long countEffectiveAdministrators(Instant now) {
		return userRepository.countEffectiveAdministrators(now, UserStatus.ACTIVE, UserAccessStatus.ACTIVE);
	}

	@Override
	public List<RoleOption> listActiveRoles() {
		return roleRepository.findByStatusOrderByNameAsc("ACTIVE").stream()
				.filter(role -> !"USER".equals(role.getCode()))
				.map(role -> new RoleOption(role.getCode(), role.getName())).toList();
	}

	private UserJpaEntity findInternalUser(String publicId) {
		UserJpaEntity entity = userRepository.findByPublicId(publicId)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario solicitado no existe."));
		boolean internal = entity.getRoles().stream().anyMatch(role -> !"USER".equals(role.getCode()));
		if (!internal) {
			throw new BusinessException("USER_NOT_FOUND", "El usuario solicitado no existe.");
		}
		return entity;
	}

	private UserJpaEntity mutableUser(String publicId) {
		var entity = findInternalUser(publicId);
		if (entity.getStatus() == UserStatus.DELETED) {
			throw new BusinessException("USER_DELETED", "El usuario está eliminado. Restáuralo antes de modificarlo.");
		}
		return entity;
	}

	private RoleJpaEntity activeRole(String code) {
		String normalized = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
		return roleRepository.findByCodeIgnoreCase(normalized)
				.filter(role -> !"USER".equals(role.getCode()))
				.filter(role -> "ACTIVE".equals(role.getStatus())).orElseThrow(
				() -> new BusinessException("ROLE_NOT_FOUND", "El rol seleccionado no existe o está inactivo."));
	}

	private void validateOrganizationRequirement(String roleCode, String organizationPublicId) {
		if (!"ADMINISTRATOR".equals(roleCode)
				&& (organizationPublicId == null || organizationPublicId.isBlank())) {
			throw new BusinessException("USER_ORGANIZATION_REQUIRED",
					"Los roles organizacionales deben pertenecer a una organización comercial.");
		}
	}

	private void assignOrganization(Long userId, String roleCode, String organizationPublicId, Long actorUserId,
			Instant changedAt) {
		var organization = resolveOrganization(roleCode, organizationPublicId);
		var membership = membershipRepository.findById(userId).orElseGet(
				() -> UserOrganizationMembershipJpaEntity.active(userId, organization.getId(), actorUserId, changedAt));
		membership.reassign(organization.getId(), actorUserId, changedAt);
		membershipRepository.saveAndFlush(membership);
	}

	private com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity resolveOrganization(
			String roleCode, String organizationPublicId) {
		if ("ADMINISTRATOR".equals(roleCode)) {
			var global = organizationRepository.findByCode(
					com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity.GLOBAL_CODE)
					.orElseThrow(() -> new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND",
							"La organización GLOBAL no se encuentra configurada."));
			if (organizationPublicId != null && !organizationPublicId.isBlank()
					&& !global.getPublicId().equals(organizationPublicId.trim())) {
				throw new BusinessException("ADMIN_GLOBAL_ORGANIZATION_REQUIRED",
						"Los Administradores globales deben pertenecer a la organización GLOBAL.");
			}
			return global;
		}

		if (organizationPublicId == null || organizationPublicId.isBlank()) {
			throw new BusinessException("USER_ORGANIZATION_REQUIRED",
					"Selecciona una organización comercial activa y vigente.");
		}
		var organization = organizationRepository.findByPublicId(organizationPublicId.trim()).orElseThrow(
				() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización seleccionada no existe."));
		if (organization.getOrganizationType() != OrganizationType.CUSTOMER) {
			throw new BusinessException("CUSTOMER_ORGANIZATION_REQUIRED",
					"Los roles organizacionales no pueden pertenecer a la organización GLOBAL.");
		}
		if (!organization.isOperational(LocalDate.now(clock))) {
			throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL",
					"La organización seleccionada está inactiva o fuera de vigencia.");
		}
		return organization;
	}

	private AdminUserSummary toSummary(UserJpaEntity entity) {
		Set<String> roles = entity.getRoles().stream().map(RoleJpaEntity::getCode)
				.filter(code -> !"USER".equals(code)).collect(Collectors.toUnmodifiableSet());
		var access = entity.getAccess();
		var membership = membershipRepository.findByUserIdAndStatus(entity.getId(), "ACTIVE").orElse(null);
		String organizationPublicId = null;
		String organizationName = null;
		if (membership != null) {
			var organization = organizationRepository.findById(membership.getOrganizationId()).orElse(null);
			if (organization != null) {
				organizationPublicId = organization.getPublicId();
				organizationName = organization.getName();
			}
		}
		String statusActor = entity.getStatusChangedBy() == null ? "Sistema"
				: userRepository.findById(entity.getStatusChangedBy()).map(UserJpaEntity::getDisplayName)
						.orElse("Usuario no disponible");
		return new AdminUserSummary(entity.getPublicId(), entity.getEmail(), entity.getFirstName(),
				entity.getLastName(), entity.getDisplayName(), entity.getStatus(), roles, organizationPublicId,
				organizationName, access.toDomain().effectiveStatusAt(clock.instant()), access.getStartsAt(),
				access.getExpiresAt(), entity.getLastLoginAt(), entity.getCreatedAt(), entity.getStatusChangedAt(),
				statusActor, entity.getStatusReason());
	}
}
