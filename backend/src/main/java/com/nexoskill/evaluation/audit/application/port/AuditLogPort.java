package com.nexoskill.evaluation.audit.application.port;

import java.time.Instant;
import java.util.Map;

public interface AuditLogPort {

    void record(
            Long userId,
            String eventType,
            String moduleCode,
            String description,
            String ipAddress,
            String userAgent,
            Map<String, Object> eventData,
            Instant occurredAt
    );
}
