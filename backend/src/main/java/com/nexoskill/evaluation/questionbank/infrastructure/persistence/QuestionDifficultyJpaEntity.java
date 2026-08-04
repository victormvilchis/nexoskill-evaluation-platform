package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_DIFFICULTY_CATALOG")
public class QuestionDifficultyJpaEntity {
	@Id
	@Column(name = "DIFFICULTY_CODE", length = 40)
	private String code;

	@Column(name = "DIFFICULTY_NAME", nullable = false)
	private String name;

	@Column(name = "DESCRIPTION", length = 500)
	private String description;

	@Column(name = "SORT_ORDER", nullable = false)
	private int sortOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false)
	private CatalogStatus status;

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

	protected QuestionDifficultyJpaEntity() {
	}

	public static QuestionDifficultyJpaEntity create(String code, String name, String description, int displayOrder,
			Long actorId, Instant now) {
		QuestionDifficultyJpaEntity entity = new QuestionDifficultyJpaEntity();
		entity.code = code;
		entity.name = name;
		entity.description = description;
		entity.sortOrder = displayOrder;
		entity.status = CatalogStatus.ACTIVE;
		entity.createdBy = actorId;
		entity.updatedBy = actorId;
		entity.createdAt = now;
		entity.updatedAt = now;
		return entity;
	}

	public void update(String name, String description, int displayOrder, Long actorId, Instant now) {
		this.name = name;
		this.description = description;
		this.sortOrder = displayOrder;
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

	public int getSortOrder() {
		return sortOrder;
	}

	public CatalogStatus getStatus() {
		return status;
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
