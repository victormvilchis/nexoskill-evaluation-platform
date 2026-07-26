package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalUserOrganizationMembershipRepository
		extends JpaRepository<UserOrganizationMembershipJpaEntity, Long> {
	Optional<UserOrganizationMembershipJpaEntity> findByUserIdAndStatus(Long userId, String status);
}
