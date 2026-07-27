package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionResultStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "CONTENT_DISTRIBUTION_RESULT")
public class ContentDistributionResultJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DISTRIBUTION_RESULT_ID")
    private Long id;

    @Column(name = "DISTRIBUTION_JOB_ID", nullable = false)
    private Long jobId;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private DistributionResultStatus status;

    @Column(name = "GRANT_ID")
    private Long grantId;

    @Column(name = "TARGET_CONTENT_ID")
    private Long targetContentId;

    @Column(name = "TARGET_CONTENT_PUBLIC_ID", length = 36)
    private String targetContentPublicId;

    @Column(name = "ERROR_CODE", length = 100)
    private String errorCode;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    @Column(name = "ATTEMPT_NUMBER", nullable = false)
    private int attemptNumber;

    @Column(name = "PROCESSED_AT", nullable = false)
    private Instant processedAt;

    protected ContentDistributionResultJpaEntity() {}

    public static ContentDistributionResultJpaEntity success(
            Long jobId, Long organizationId, Long grantId, Long targetContentId,
            String targetContentPublicId, Instant now) {
        ContentDistributionResultJpaEntity entity = base(jobId, organizationId, now);
        entity.status = DistributionResultStatus.SUCCESS;
        entity.grantId = grantId;
        entity.targetContentId = targetContentId;
        entity.targetContentPublicId = targetContentPublicId;
        return entity;
    }

    public static ContentDistributionResultJpaEntity skipped(
            Long jobId, Long organizationId, Long grantId, Long targetContentId,
            String targetContentPublicId, Instant now) {
        ContentDistributionResultJpaEntity entity = base(jobId, organizationId, now);
        entity.status = DistributionResultStatus.SKIPPED;
        entity.grantId = grantId;
        entity.targetContentId = targetContentId;
        entity.targetContentPublicId = targetContentPublicId;
        return entity;
    }

    public static ContentDistributionResultJpaEntity failed(
            Long jobId, Long organizationId, String errorCode, String errorMessage, Instant now) {
        ContentDistributionResultJpaEntity entity = base(jobId, organizationId, now);
        entity.status = DistributionResultStatus.FAILED;
        entity.errorCode = errorCode;
        entity.errorMessage = errorMessage;
        return entity;
    }

    private static ContentDistributionResultJpaEntity base(Long jobId, Long organizationId, Instant now) {
        ContentDistributionResultJpaEntity entity = new ContentDistributionResultJpaEntity();
        entity.jobId = jobId;
        entity.organizationId = organizationId;
        entity.attemptNumber = 1;
        entity.processedAt = now;
        return entity;
    }

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public Long getOrganizationId() { return organizationId; }
    public DistributionResultStatus getStatus() { return status; }
    public Long getGrantId() { return grantId; }
    public Long getTargetContentId() { return targetContentId; }
    public String getTargetContentPublicId() { return targetContentPublicId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getProcessedAt() { return processedAt; }
}
