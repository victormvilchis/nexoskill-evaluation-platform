package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_TYPE_CATALOG")
public class QuestionTypeJpaEntity {
	@Id
	@Column(name = "TYPE_CODE", length = 40)
	private String code;

	@Column(name = "TYPE_NAME", nullable = false)
	private String name;

	@Column(name = "DESCRIPTION", length = 500)
	private String description;

	@Column(name = "SUPPORTS_OPTIONS", nullable = false)
	private boolean supportsOptions;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false)
	private CatalogStatus status;

	@Column(name = "DISPLAY_ORDER", nullable = false)
	private int displayOrder;

	@Column(name = "CREATED_BY")
	private Long createdBy;

	@Column(name = "UPDATED_BY")
	private Long updatedBy;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "UPDATED_AT", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private Long version;

	protected QuestionTypeJpaEntity() {
	}

	public static QuestionTypeJpaEntity create(String code, String name, String description, int displayOrder,
			Long actorId, Instant now) {
		QuestionTypeJpaEntity entity = new QuestionTypeJpaEntity();
		entity.code = code;
		entity.name = name;
		entity.description = description;
		entity.supportsOptions = true;
		entity.status = CatalogStatus.ACTIVE;
		entity.displayOrder = displayOrder;
		entity.createdBy = actorId;
		entity.updatedBy = actorId;
		entity.createdAt = now;
		entity.updatedAt = now;
		return entity;
	}

	public void update(String name, String description, int displayOrder, Long actorId, Instant now) {
		this.name = name;
		this.description = description;
		this.displayOrder = displayOrder;
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public void changeStatus(CatalogStatus status, Long actorId, Instant now) {
		this.status = status;
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isSupportsOptions() {
		return supportsOptions;
	}

	public CatalogStatus getStatus() {
		return status;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getVersion() {
		return version;
	}
}
