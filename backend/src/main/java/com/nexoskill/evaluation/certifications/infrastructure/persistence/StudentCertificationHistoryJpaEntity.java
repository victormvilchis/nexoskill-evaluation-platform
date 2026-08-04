package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "STUDENT_CERTIFICATION_HISTORY")
public class StudentCertificationHistoryJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "STUDENT_CERTIFICATION_HISTORY_ID")
	private Long id;
	@Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true)
	private String publicId;
	@Column(name = "STUDENT_ID", nullable = false)
	private Long studentId;
	@Column(name = "ORGANIZATION_ID", nullable = false)
	private Long organizationId;
	@Column(name = "STUDENT_CERT_REQUIREMENT_ID")
	private Long requirementId;
	@Column(name = "EVENT_TYPE", nullable = false, length = 80)
	private String eventType;
	@Lob
	@Column(name = "PREVIOUS_VALUES")
	private String previousValues;
	@Lob
	@Column(name = "NEW_VALUES")
	private String newValues;
	@Column(name = "REASON", length = 500)
	private String reason;
	@Column(name = "CHANGED_BY", nullable = false)
	private Long changedBy;
	@Column(name = "CHANGED_AT", nullable = false)
	private Instant changedAt;

	protected StudentCertificationHistoryJpaEntity() {
	}

	public static StudentCertificationHistoryJpaEntity create(Long studentId, Long organizationId, Long requirementId,
			String eventType, String previousValues, String newValues, String reason, Long actorId, Instant now) {
		StudentCertificationHistoryJpaEntity entity = new StudentCertificationHistoryJpaEntity();
		entity.publicId = java.util.UUID.randomUUID().toString();
		entity.studentId = studentId;
		entity.organizationId = organizationId;
		entity.requirementId = requirementId;
		entity.eventType = eventType;
		entity.previousValues = previousValues;
		entity.newValues = newValues;
		entity.reason = reason;
		entity.changedBy = actorId;
		entity.changedAt = now;
		return entity;
	}

	public String getPublicId() {
		return publicId;
	}

	public Long getRequirementId() {
		return requirementId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPreviousValues() {
		return previousValues;
	}

	public String getNewValues() {
		return newValues;
	}

	public String getReason() {
		return reason;
	}

	public Instant getChangedAt() {
		return changedAt;
	}
}
