package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionJobStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "CONTENT_DISTRIBUTION_JOB")
public class ContentDistributionJobJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "DISTRIBUTION_JOB_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Enumerated(EnumType.STRING)
	@Column(name = "CONTENT_TYPE", nullable = false, length = 30)
	private GlobalContentType contentType;

	@Column(name = "GLOBAL_CONTENT_ID", nullable = false)
	private Long globalContentId;

	@Column(name = "GLOBAL_CONTENT_PUBLIC_ID", nullable = false, length = 36)
	private String globalContentPublicId;

	@Column(name = "GLOBAL_VERSION", nullable = false)
	private long globalVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "DISTRIBUTION_MODE", nullable = false, length = 30)
	private DistributionMode distributionMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 30)
	private DistributionJobStatus status;

	@Column(name = "TOTAL_ORGANIZATIONS", nullable = false)
	private int totalOrganizations;

	@Column(name = "PROCESSED_ORGANIZATIONS", nullable = false)
	private int processedOrganizations;

	@Column(name = "SUCCESSFUL_ORGANIZATIONS", nullable = false)
	private int successfulOrganizations;

	@Column(name = "FAILED_ORGANIZATIONS", nullable = false)
	private int failedOrganizations;

	@Column(name = "SKIPPED_ORGANIZATIONS", nullable = false)
	private int skippedOrganizations;

	@Column(name = "REQUESTED_BY", nullable = false)
	private Long requestedBy;

	@Column(name = "REQUESTED_AT", nullable = false)
	private Instant requestedAt;

	@Column(name = "STARTED_AT")
	private Instant startedAt;

	@Column(name = "FINISHED_AT")
	private Instant finishedAt;

	@Column(name = "REQUEST_NOTES", length = 2000)
	private String requestNotes;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private long entityVersion;

	protected ContentDistributionJobJpaEntity() {
	}

	public static ContentDistributionJobJpaEntity create(String publicId, GlobalContentType contentType,
			Long globalContentId, String globalContentPublicId, long globalVersion, DistributionMode distributionMode,
			int totalOrganizations, Long actor, String notes, Instant now) {
		ContentDistributionJobJpaEntity entity = new ContentDistributionJobJpaEntity();
		entity.publicId = publicId;
		entity.contentType = contentType;
		entity.globalContentId = globalContentId;
		entity.globalContentPublicId = globalContentPublicId;
		entity.globalVersion = globalVersion;
		entity.distributionMode = distributionMode;
		entity.status = DistributionJobStatus.PENDING;
		entity.totalOrganizations = totalOrganizations;
		entity.requestedBy = actor;
		entity.requestedAt = now;
		entity.requestNotes = notes;
		return entity;
	}

	public void start(Instant now) {
		status = DistributionJobStatus.RUNNING;
		startedAt = now;
	}

	public void registerSuccess() {
		processedOrganizations++;
		successfulOrganizations++;
	}

	public void registerSkipped() {
		processedOrganizations++;
		skippedOrganizations++;
	}

	public void registerFailure() {
		processedOrganizations++;
		failedOrganizations++;
	}

	public void finish(Instant now) {
		finishedAt = now;
		status = failedOrganizations == 0 ? DistributionJobStatus.COMPLETED
				: successfulOrganizations > 0 || skippedOrganizations > 0 ? DistributionJobStatus.COMPLETED_WITH_ERRORS
						: DistributionJobStatus.FAILED;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public GlobalContentType getContentType() {
		return contentType;
	}

	public Long getGlobalContentId() {
		return globalContentId;
	}

	public String getGlobalContentPublicId() {
		return globalContentPublicId;
	}

	public long getGlobalVersion() {
		return globalVersion;
	}

	public DistributionMode getDistributionMode() {
		return distributionMode;
	}

	public DistributionJobStatus getStatus() {
		return status;
	}

	public int getTotalOrganizations() {
		return totalOrganizations;
	}

	public int getProcessedOrganizations() {
		return processedOrganizations;
	}

	public int getSuccessfulOrganizations() {
		return successfulOrganizations;
	}

	public int getFailedOrganizations() {
		return failedOrganizations;
	}

	public int getSkippedOrganizations() {
		return skippedOrganizations;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
