package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "STUDENT_CERTIFICATION_ATTEMPT")
public class StudentCertificationAttemptJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "STUDENT_CERTIFICATION_ATTEMPT_ID")
	private Long id;
	@Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true)
	private String publicId;
	@Column(name = "STUDENT_CERT_REQUIREMENT_ID", nullable = false)
	private Long requirementId;
	@Column(name = "ORGANIZATION_ID", nullable = false)
	private Long organizationId;
	@Column(name = "ATTEMPT_NUMBER", nullable = false)
	private int attemptNumber;
	@Column(name = "SCHEDULED_DATE")
	private LocalDate scheduledDate;
	@Column(name = "APPLICATION_DATE")
	private LocalDate applicationDate;
	@Enumerated(EnumType.STRING)
	@Column(name = "EXAM_STATUS", nullable = false, length = 30)
	private CertificationExamStatus examStatus;
	@Column(name = "SCORE", precision = 5, scale = 2)
	private BigDecimal score;
	@Column(name = "RESULT", length = 30)
	private String result;
	@Column(name = "OBSERVATIONS", length = 1000)
	private String observations;
	@Column(name = "CREATED_BY", nullable = false)
	private Long createdBy;
	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	protected StudentCertificationAttemptJpaEntity() {
	}

	public static StudentCertificationAttemptJpaEntity create(Long requirementId, Long organizationId,
			int attemptNumber, LocalDate scheduledDate, LocalDate applicationDate, CertificationExamStatus examStatus,
			BigDecimal score, String result, String observations, Long actorId, Instant now) {
		if (attemptNumber < 1)
			throw new BusinessException("CERTIFICATION_ATTEMPT_INVALID", "El intento debe iniciar en 1.");
		if (score != null && (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0)) {
			throw new BusinessException("CERTIFICATION_SCORE_INVALID", "El promedio debe estar entre 0 y 100.");
		}
		String normalizedResult = result == null || result.isBlank() ? null
				: result.trim().toUpperCase(java.util.Locale.ROOT);
		if (normalizedResult != null
				&& !java.util.Set.of("PASSED", "FAILED", "ABSENT", "CANCELLED", "PENDING").contains(normalizedResult)) {
			throw new BusinessException("CERTIFICATION_ATTEMPT_RESULT_INVALID",
					"El resultado del intento no es válido.");
		}
		StudentCertificationAttemptJpaEntity entity = new StudentCertificationAttemptJpaEntity();
		entity.publicId = java.util.UUID.randomUUID().toString();
		entity.requirementId = requirementId;
		entity.organizationId = organizationId;
		entity.attemptNumber = attemptNumber;
		entity.scheduledDate = scheduledDate;
		entity.applicationDate = applicationDate;
		entity.examStatus = examStatus;
		entity.score = score;
		entity.result = normalizedResult;
		entity.observations = observations == null || observations.isBlank() ? null : observations.trim();
		entity.createdBy = actorId;
		entity.createdAt = now;
		return entity;
	}

	public String getPublicId() {
		return publicId;
	}

	public Long getRequirementId() {
		return requirementId;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public LocalDate getScheduledDate() {
		return scheduledDate;
	}

	public LocalDate getApplicationDate() {
		return applicationDate;
	}

	public CertificationExamStatus getExamStatus() {
		return examStatus;
	}

	public BigDecimal getScore() {
		return score;
	}

	public String getResult() {
		return result;
	}

	public String getObservations() {
		return observations;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
