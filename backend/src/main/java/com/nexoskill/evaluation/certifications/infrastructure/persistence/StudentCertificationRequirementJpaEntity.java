package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import com.nexoskill.evaluation.certifications.domain.*;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "STUDENT_CERT_REQUIREMENT")
public class StudentCertificationRequirementJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STUDENT_CERT_REQUIREMENT_ID") private Long id;
    @Column(name = "PUBLIC_ID", nullable = false, length = 36, unique = true) private String publicId;
    @Column(name = "STUDENT_CERTIFICATION_PROFILE_ID", nullable = false) private Long profileId;
    @Column(name = "ORGANIZATION_ID", nullable = false) private Long organizationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "CERTIFICATION_TYPE", nullable = false, length = 40) private CertificationType certificationType;
    @Column(name = "APPLIES", nullable = false) private boolean applies;
    @Enumerated(EnumType.STRING)
    @Column(name = "CERTIFICATION_STATUS", nullable = false, length = 30) private CertificationStatus certificationStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "EXAM_STATUS", nullable = false, length = 30) private CertificationExamStatus examStatus;
    @Column(name = "CALCULATED_DEADLINE") private LocalDate calculatedDeadline;
    @Column(name = "MANUAL_DEADLINE") private LocalDate manualDeadline;
    @Column(name = "DEADLINE_OVERRIDE_REASON", length = 500) private String deadlineOverrideReason;
    @Column(name = "DEADLINE_ADJUSTED_BY") private Long deadlineAdjustedBy;
    @Column(name = "DEADLINE_ADJUSTED_AT") private Instant deadlineAdjustedAt;
    @Column(name = "APPLICATION_DATE") private LocalDate applicationDate;
    @Column(name = "SCORE", precision = 5, scale = 2) private BigDecimal score;
    @Column(name = "CURRENT_ATTEMPT") private Integer currentAttempt;
    @Column(name = "ACTIONS_TO_TAKE", length = 1000) private String actionsToTake;
    @Column(name = "OBSERVATIONS", length = 1000) private String observations;
    @Column(name = "CREATED_BY", nullable = false) private Long createdBy;
    @Column(name = "UPDATED_BY", nullable = false) private Long updatedBy;
    @Column(name = "CREATED_AT", nullable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private Long version;
    protected StudentCertificationRequirementJpaEntity() {}
    public static StudentCertificationRequirementJpaEntity create(Long profileId, Long organizationId,
            CertificationType type, LocalDate calculatedDeadline, Long actorId, Instant now) {
        StudentCertificationRequirementJpaEntity entity = new StudentCertificationRequirementJpaEntity();
        entity.publicId = java.util.UUID.randomUUID().toString();
        entity.profileId = profileId;
        entity.organizationId = organizationId;
        entity.certificationType = type;
        entity.applies = false;
        entity.certificationStatus = CertificationStatus.NOT_APPLICABLE;
        entity.examStatus = CertificationExamStatus.NOT_SCHEDULED;
        entity.calculatedDeadline = calculatedDeadline;
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
    public void update(boolean applies, CertificationStatus certificationStatus,
            CertificationExamStatus examStatus, LocalDate calculatedDeadline, LocalDate manualDeadline,
            String deadlineOverrideReason, LocalDate applicationDate, BigDecimal score, Integer currentAttempt,
            String actionsToTake, String observations, Long actorId, Instant now) {
        validate(score, currentAttempt, manualDeadline, deadlineOverrideReason);
        this.applies = applies;
        this.certificationStatus = applies ? certificationStatus : CertificationStatus.NOT_APPLICABLE;
        this.examStatus = applies ? examStatus : CertificationExamStatus.NOT_SCHEDULED;
        this.calculatedDeadline = calculatedDeadline;
        if (manualDeadline != null) {
            boolean changed = !java.util.Objects.equals(this.manualDeadline, manualDeadline);
            this.manualDeadline = manualDeadline;
            this.deadlineOverrideReason = deadlineOverrideReason.trim();
            if (changed) {
                this.deadlineAdjustedBy = actorId;
                this.deadlineAdjustedAt = now;
            }
        } else {
            this.manualDeadline = null;
            this.deadlineOverrideReason = null;
            this.deadlineAdjustedBy = null;
            this.deadlineAdjustedAt = null;
        }
        this.applicationDate = applicationDate;
        this.score = score;
        this.currentAttempt = currentAttempt;
        this.actionsToTake = trimToNull(actionsToTake);
        this.observations = trimToNull(observations);
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
    private static void validate(BigDecimal score, Integer currentAttempt, LocalDate manualDeadline, String reason) {
        if (score != null && (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessException("CERTIFICATION_SCORE_INVALID", "El promedio debe estar entre 0 y 100.");
        }
        if (currentAttempt != null && currentAttempt < 1) {
            throw new BusinessException("CERTIFICATION_ATTEMPT_INVALID", "El intento debe ser un entero positivo.");
        }
        if (manualDeadline != null && (reason == null || reason.isBlank())) {
            throw new BusinessException("CERTIFICATION_DEADLINE_REASON_REQUIRED",
                    "Indica el motivo del ajuste manual de la fecha límite.");
        }
        if (reason != null && reason.length() > 500) {
            throw new BusinessException("CERTIFICATION_DEADLINE_REASON_INVALID",
                    "El motivo del ajuste no puede superar 500 caracteres.");
        }
    }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public LocalDate effectiveDeadline() { return manualDeadline == null ? calculatedDeadline : manualDeadline; }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getProfileId() { return profileId; }
    public Long getOrganizationId() { return organizationId; }
    public CertificationType getCertificationType() { return certificationType; }
    public boolean isApplies() { return applies; }
    public CertificationStatus getCertificationStatus() { return certificationStatus; }
    public CertificationExamStatus getExamStatus() { return examStatus; }
    public LocalDate getCalculatedDeadline() { return calculatedDeadline; }
    public LocalDate getManualDeadline() { return manualDeadline; }
    public String getDeadlineOverrideReason() { return deadlineOverrideReason; }
    public LocalDate getApplicationDate() { return applicationDate; }
    public BigDecimal getScore() { return score; }
    public Integer getCurrentAttempt() { return currentAttempt; }
    public String getActionsToTake() { return actionsToTake; }
    public String getObservations() { return observations; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
