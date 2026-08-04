package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationTechnologyRepository extends JpaRepository<CertificationTechnologyJpaEntity, Long> {
	Optional<CertificationTechnologyJpaEntity> findByPublicIdAndStatus(String publicId, String status);

	Optional<CertificationTechnologyJpaEntity> findByMasterTechnologyIdAndStatus(Long masterTechnologyId,
			String status);

	Optional<CertificationTechnologyJpaEntity> findByMasterTechnologyId(Long masterTechnologyId);

	Optional<CertificationTechnologyJpaEntity> findByCodeIgnoreCase(String code);

	List<CertificationTechnologyJpaEntity> findAllByStatusOrderBySortOrderAscNameAsc(String status);
}
