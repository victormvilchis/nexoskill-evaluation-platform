package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.SyncStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "CONTENT_REPLICATION_LINK")
public class ContentReplicationLinkJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "REPLICATION_LINK_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Column(name = "ORGANIZATION_ID", nullable = false)
	private Long organizationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "CONTENT_TYPE", nullable = false, length = 30)
	private GlobalContentType contentType;

	@Column(name = "SOURCE_GLOBAL_CONTENT_ID", nullable = false)
	private Long sourceGlobalContentId;

	@Column(name = "SOURCE_GLOBAL_PUBLIC_ID", nullable = false, length = 36)
	private String sourceGlobalPublicId;

	@Column(name = "SOURCE_GLOBAL_VERSION", nullable = false)
	private long sourceGlobalVersion;

	@Column(name = "TARGET_CONTENT_ID", nullable = false)
	private Long targetContentId;

	@Column(name = "TARGET_CONTENT_PUBLIC_ID", nullable = false, length = 36)
	private String targetContentPublicId;

	@Column(name = "IS_CUSTOMIZED", nullable = false)
	private Integer customized;

	@Enumerated(EnumType.STRING)
	@Column(name = "SYNC_STATUS", nullable = false, length = 30)
	private SyncStatus syncStatus;

	@Column(name = "LAST_SYNCHRONIZED_AT", nullable = false)
	private Instant lastSynchronizedAt;

	@Column(name = "CREATED_BY", nullable = false)
	private Long createdBy;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private long entityVersion;

	protected ContentReplicationLinkJpaEntity() {
	}

	public static ContentReplicationLinkJpaEntity create(String publicId, Long organizationId,
			GlobalContentType contentType, Long sourceGlobalContentId, String sourceGlobalPublicId,
			long sourceGlobalVersion, Long targetContentId, String targetContentPublicId, Long actor, Instant now) {
		ContentReplicationLinkJpaEntity entity = new ContentReplicationLinkJpaEntity();
		entity.publicId = publicId;
		entity.organizationId = organizationId;
		entity.contentType = contentType;
		entity.sourceGlobalContentId = sourceGlobalContentId;
		entity.sourceGlobalPublicId = sourceGlobalPublicId;
		entity.sourceGlobalVersion = sourceGlobalVersion;
		entity.targetContentId = targetContentId;
		entity.targetContentPublicId = targetContentPublicId;
		entity.customized = 0;
		entity.syncStatus = SyncStatus.SYNCHRONIZED;
		entity.lastSynchronizedAt = now;
		entity.createdBy = actor;
		entity.createdAt = now;
		return entity;
	}

	public void markCustomized() {
		customized = 1;
		syncStatus = SyncStatus.DIVERGED;
	}

	public void markUpdateAvailable() {
		if (!isCustomized())
			syncStatus = SyncStatus.UPDATE_AVAILABLE;
	}

	public void markSynchronized(long version, Instant now) {
		sourceGlobalVersion = version;
		customized = 0;
		syncStatus = SyncStatus.SYNCHRONIZED;
		lastSynchronizedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public GlobalContentType getContentType() {
		return contentType;
	}

	public Long getSourceGlobalContentId() {
		return sourceGlobalContentId;
	}

	public String getSourceGlobalPublicId() {
		return sourceGlobalPublicId;
	}

	public long getSourceGlobalVersion() {
		return sourceGlobalVersion;
	}

	public Long getTargetContentId() {
		return targetContentId;
	}

	public String getTargetContentPublicId() {
		return targetContentPublicId;
	}

	public boolean isCustomized() {
		return Integer.valueOf(1).equals(customized);
	}

	public SyncStatus getSyncStatus() {
		return syncStatus;
	}

	public Instant getLastSynchronizedAt() {
		return lastSynchronizedAt;
	}
}
