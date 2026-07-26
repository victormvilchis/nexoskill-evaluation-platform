package com.nexoskill.evaluation.shared.infrastructure.config;

import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.InternalUserOrganizationMembershipRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.UserOrganizationMembershipJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.RoleJpaEntity;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataRoleJpaRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataUserJpaRepository;
import com.nexoskill.evaluation.users.infrastructure.persistence.UserJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialDataSeeder implements ApplicationRunner {

	private final AppProperties properties;
	private final SpringDataUserJpaRepository userRepository;
	private final SpringDataRoleJpaRepository roleRepository;
	private final PasswordHasher passwordHasher;
	private final OrganizationRepository organizationRepository;
	private final InternalUserOrganizationMembershipRepository membershipRepository;

	public InitialDataSeeder(AppProperties properties, SpringDataUserJpaRepository userRepository,
			SpringDataRoleJpaRepository roleRepository, PasswordHasher passwordHasher,
			OrganizationRepository organizationRepository,
			InternalUserOrganizationMembershipRepository membershipRepository) {
		this.properties = properties;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordHasher = passwordHasher;
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!properties.getSeed().isEnabled()) {
			return;
		}

		createIfMissing(properties.getSeed().getAdminEmail(), properties.getSeed().getAdminPassword(), "Administrador",
				"NexoSkill", "Administrador NexoSkill", "ADMINISTRATOR");

	}

	private void createIfMissing(String email, String password, String firstName, String lastName, String displayName,
			String roleCode) {

		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			return;
		}

		String normalizedEmail = EmailNormalizer.normalize(email);
		RoleJpaEntity role = roleRepository.findByCode(roleCode)
				.orElseThrow(() -> new IllegalStateException("No existe el rol inicial " + roleCode));
		OrganizationJpaEntity global = organizationRepository.findByCode(OrganizationJpaEntity.GLOBAL_CODE)
				.orElseThrow(() -> new IllegalStateException("No existe la organización GLOBAL inicial"));

		Instant now = Instant.now();
		UserJpaEntity user = userRepository.findByNormalizedEmail(normalizedEmail).orElseGet(() ->
				userRepository.saveAndFlush(UserJpaEntity.create(UUID.randomUUID().toString(), email.trim(),
						normalizedEmail, passwordHasher.encode(password), firstName, lastName, displayName, role, now,
						null, com.nexoskill.evaluation.users.domain.model.UserStatus.ACTIVE, false, now, null, null, now)));

		if ("ADMINISTRATOR".equals(roleCode)) {
			membershipRepository.findById(user.getId()).ifPresentOrElse(
					membership -> membership.reassign(global.getId(), null, now),
					() -> membershipRepository.save(UserOrganizationMembershipJpaEntity.active(
							user.getId(), global.getId(), null, now)));
		}
	}
}
