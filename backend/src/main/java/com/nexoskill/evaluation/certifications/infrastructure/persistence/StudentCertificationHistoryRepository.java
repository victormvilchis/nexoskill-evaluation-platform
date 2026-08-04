package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificationHistoryRepository
		extends JpaRepository<StudentCertificationHistoryJpaEntity, Long> {
	List<StudentCertificationHistoryJpaEntity> findTop100ByStudentIdAndOrganizationIdOrderByChangedAtDesc(
			Long studentId, Long organizationId);
}
