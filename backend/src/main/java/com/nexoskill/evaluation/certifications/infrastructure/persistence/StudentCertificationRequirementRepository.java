package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.certifications.domain.CertificationType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificationRequirementRepository
		extends JpaRepository<StudentCertificationRequirementJpaEntity, Long> {
	List<StudentCertificationRequirementJpaEntity> findAllByProfileIdOrderByCertificationTypeAsc(Long profileId);

	Optional<StudentCertificationRequirementJpaEntity> findByProfileIdAndCertificationType(Long profileId,
			CertificationType type);
}
