package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "QUESTION_DIFFICULTY_CATALOG")
public class QuestionDifficultyJpaEntity {
	@Id
	@Column(name = "DIFFICULTY_CODE", length = 40)
	private String code;
	@Column(name = "DIFFICULTY_NAME", nullable = false)
	private String name;
	@Column(name = "SORT_ORDER", nullable = false)
	private int sortOrder;
	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false)
	private CatalogStatus status;

	protected QuestionDifficultyJpaEntity() {
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public CatalogStatus getStatus() {
		return status;
	}
}
