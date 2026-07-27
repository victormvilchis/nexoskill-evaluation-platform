package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentDistributionResultRepository extends JpaRepository<ContentDistributionResultJpaEntity, Long> {
    List<ContentDistributionResultJpaEntity> findAllByJobIdOrderByProcessedAtAsc(Long jobId);
    Optional<ContentDistributionResultJpaEntity> findByJobIdAndOrganizationId(Long jobId, Long organizationId);
}
