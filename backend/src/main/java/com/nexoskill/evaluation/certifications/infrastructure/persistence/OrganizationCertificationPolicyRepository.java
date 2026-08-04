package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.certifications.domain.CertificationType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationCertificationPolicyRepository
		extends JpaRepository<OrganizationCertificationPolicyJpaEntity, Long> {
	List<OrganizationCertificationPolicyJpaEntity> findAllByOrganizationIdAndStatus(Long organizationId, String status);

	Optional<OrganizationCertificationPolicyJpaEntity> findByOrganizationIdAndCertificationType(Long organizationId,
			CertificationType type);
}
