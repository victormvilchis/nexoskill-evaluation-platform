package com.nexoskill.evaluation.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "AUDIT_EVENT")
public class AuditEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_EVENT_ID")
    private Long id;

    @Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 100)
    private String eventType;

    @Column(name = "MODULE_CODE", nullable = false, length = 50)
    private String moduleCode;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "IP_ADDRESS", length = 64)
    private String ipAddress;

    @Column(name = "USER_AGENT", length = 1000)
    private String userAgent;

    @Lob
    @Column(name = "EVENT_DATA")
    private String eventData;

    @Column(name = "OCCURRED_AT", nullable = false)
    private Instant occurredAt;

    protected AuditEventJpaEntity() {
    }

    public static AuditEventJpaEntity create(
            String publicId,
            Long userId,
            String eventType,
            String moduleCode,
            String description,
            String ipAddress,
            String userAgent,
            String eventData,
            Instant occurredAt) {
        AuditEventJpaEntity entity = new AuditEventJpaEntity();
        entity.publicId = publicId;
        entity.userId = userId;
        entity.eventType = eventType;
        entity.moduleCode = moduleCode;
        entity.description = description;
        entity.ipAddress = ipAddress;
        entity.userAgent = userAgent;
        entity.eventData = eventData;
        entity.occurredAt = occurredAt;
        return entity;
    }
}
