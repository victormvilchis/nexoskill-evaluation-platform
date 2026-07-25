package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_CATEGORY")
public class QuestionCategoryJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "CATEGORY_ID")
	private Long id;
	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;
	@Column(name = "CATEGORY_CODE", nullable = false, unique = true, length = 80)
	private String code;
	@Column(name = "CATEGORY_NAME", nullable = false, length = 150)
	private String name;
	@Column(name = "DESCRIPTION", length = 500)
	private String description;
	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 20)
	private CatalogStatus status;
	@Column(name = "CREATED_BY")
	private Long createdBy;
	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;
	@Column(name = "UPDATED_BY")
	private Long updatedBy;
	@Column(name = "UPDATED_AT")
	private Instant updatedAt;
	@Version
	@Column(name = "VERSION_NO", nullable = false)
	private long version;

	protected QuestionCategoryJpaEntity() {
	}

	public static QuestionCategoryJpaEntity create(String id, String code, String name, String desc, Long actor,
			Instant now) {
		var e = new QuestionCategoryJpaEntity();
		e.publicId = id;
		e.code = code;
		e.name = name;
		e.description = desc;
		e.status = CatalogStatus.ACTIVE;
		e.createdBy = actor;
		e.createdAt = now;
		return e;
	}

	public void update(String code, String name, String desc, Long actor, Instant now) {
		this.code = code;
		this.name = name;
		this.description = desc;
		this.updatedBy = actor;
		this.updatedAt = now;
	}

	public void changeStatus(CatalogStatus s, Long actor, Instant now) {
		status = s;
		updatedBy = actor;
		updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getPublicId() {
		return publicId;
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

	public CatalogStatus getStatus() {
		return status;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
