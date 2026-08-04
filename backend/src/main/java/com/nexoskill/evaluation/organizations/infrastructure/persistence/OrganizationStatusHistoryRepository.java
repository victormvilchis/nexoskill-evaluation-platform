package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationStatusHistoryRepository extends JpaRepository<OrganizationStatusHistoryJpaEntity, Long> {
	List<OrganizationStatusHistoryJpaEntity> findByOrganizationIdOrderByChangedAtDesc(Long organizationId);
}
