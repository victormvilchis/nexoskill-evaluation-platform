package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_MEDIA")
public class QuestionMediaJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "MEDIA_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@Column(name = "STORAGE_KEY", nullable = false, unique = true, length = 500)
	private String storageKey;

	@Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
	private String originalName;

	@Column(name = "CONTENT_TYPE", nullable = false, length = 100)
	private String contentType;

	@Column(name = "FILE_SIZE", nullable = false)
	private long size;

	@Column(name = "CHECKSUM_SHA256", nullable = false, length = 64)
	private String checksum;

	@Enumerated(EnumType.STRING)
	@Column(name = "CONTENT_SCOPE", nullable = false, length = 20)
	private ContentScope contentScope;

	@Column(name = "OWNER_ORGANIZATION_ID", nullable = false)
	private Long ownerOrganizationId;

	@Column(name = "CREATED_BY", nullable = false)
	private Long createdBy;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	protected QuestionMediaJpaEntity() {
	}

	public static QuestionMediaJpaEntity create(String publicId, String storageKey, String originalName,
			String contentType, long size, String checksum, ContentScope contentScope, Long ownerOrganizationId,
			Long actor, Instant now) {
		var entity = new QuestionMediaJpaEntity();
		entity.publicId = publicId;
		entity.storageKey = storageKey;
		entity.originalName = originalName;
		entity.contentType = contentType;
		entity.size = size;
		entity.checksum = checksum;
		entity.contentScope = contentScope;
		entity.ownerOrganizationId = ownerOrganizationId;
		entity.createdBy = actor;
		entity.createdAt = now;
		return entity;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSize() {
		return size;
	}

	public ContentScope getContentScope() {
		return contentScope;
	}

	public Long getOwnerOrganizationId() {
		return ownerOrganizationId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}
}
