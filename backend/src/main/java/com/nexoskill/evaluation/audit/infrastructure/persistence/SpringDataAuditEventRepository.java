package com.nexoskill.evaluation.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAuditEventRepository
        extends JpaRepository<AuditEventJpaEntity, Long> {
}
