package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificationAttemptRepository
		extends JpaRepository<StudentCertificationAttemptJpaEntity, Long> {
	List<StudentCertificationAttemptJpaEntity> findAllByRequirementIdOrderByAttemptNumberAsc(Long requirementId);

	boolean existsByRequirementIdAndAttemptNumber(Long requirementId, int attemptNumber);
}
