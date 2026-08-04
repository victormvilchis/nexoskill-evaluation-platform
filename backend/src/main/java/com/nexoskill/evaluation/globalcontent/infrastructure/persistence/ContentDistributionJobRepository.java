package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentDistributionJobRepository extends JpaRepository<ContentDistributionJobJpaEntity, Long> {
	Optional<ContentDistributionJobJpaEntity> findByPublicId(String publicId);
}
