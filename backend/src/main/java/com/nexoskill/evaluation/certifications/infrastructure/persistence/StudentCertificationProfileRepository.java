package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificationProfileRepository
		extends JpaRepository<StudentCertificationProfileJpaEntity, Long> {
	Optional<StudentCertificationProfileJpaEntity> findByStudentIdAndOrganizationId(Long studentId,
			Long organizationId);
}
