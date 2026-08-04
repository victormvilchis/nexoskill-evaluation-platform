package com.nexoskill.evaluation.students.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalentCvRepository extends JpaRepository<TalentCvJpaEntity, Long> {
	Optional<TalentCvJpaEntity> findByStudentId(Long studentId);

	Optional<TalentCvJpaEntity> findByStudentIdAndOrganizationId(Long studentId, Long organizationId);

	boolean existsByStudentId(Long studentId);

	void deleteByStudentId(Long studentId);
}
