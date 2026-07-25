package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "QUESTION_TYPE_CATALOG")
public class QuestionTypeJpaEntity {
	@Id
	@Column(name = "TYPE_CODE", length = 40)
	private String code;
	@Column(name = "TYPE_NAME", nullable = false)
	private String name;
	@Column(name = "DESCRIPTION")
	private String description;
	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false)
	private CatalogStatus status;

	protected QuestionTypeJpaEntity() {
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
}
