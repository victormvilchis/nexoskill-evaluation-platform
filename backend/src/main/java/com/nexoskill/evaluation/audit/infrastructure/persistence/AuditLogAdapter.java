package com.nexoskill.evaluation.audit.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuditLogAdapter implements AuditLogPort {

	private final SpringDataAuditEventRepository repository;
	private final ObjectMapper objectMapper;

	public AuditLogAdapter(SpringDataAuditEventRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Override
	public void record(Long userId, String eventType, String moduleCode, String description, String ipAddress,
			String userAgent, Map<String, Object> eventData, Instant occurredAt) {

		repository.save(AuditEventJpaEntity.create(UUID.randomUUID().toString(), userId, eventType, moduleCode,
				description, ipAddress, userAgent, toJson(eventData), occurredAt));
	}

	private String toJson(Map<String, Object> eventData) {
		try {
			return objectMapper.writeValueAsString(eventData);
		} catch (JsonProcessingException exception) {
			return "{}";
		}
	}
}
