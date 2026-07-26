package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationLicensePolicyRepository extends JpaRepository<OrganizationLicensePolicyJpaEntity, Long> {
	Optional<OrganizationLicensePolicyJpaEntity> findByOrganizationId(Long organizationId);
}
