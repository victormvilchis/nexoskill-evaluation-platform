package com.nexoskill.evaluation.forms.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "FORM_QUESTION_POOL")
public class FormQuestionPoolJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "POOL_ID")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "SECTION_ID", nullable = false)
	private FormSectionJpaEntity section;

	@Column(name = "PUBLIC_ID", nullable = false, length = 36)
	private String publicId;

	@Column(name = "SOURCE_TYPE", nullable = false, length = 20)
	private String sourceType;

	@Column(name = "CATEGORY_ID")
	private Long categoryId;

	@Column(name = "QUESTION_COUNT", nullable = false)
	private Integer questionCount;

	@Column(name = "DIFFICULTY_CODE", length = 30)
	private String difficultyCode;

	@Column(name = "POOL_ORDER", nullable = false)
	private Integer poolOrder;

	protected FormQuestionPoolJpaEntity() {
	}

	static FormQuestionPoolJpaEntity category(FormSectionJpaEntity section, String publicId, Long categoryId,
			int questionCount, String difficultyCode, int order) {
		FormQuestionPoolJpaEntity entity = new FormQuestionPoolJpaEntity();
		entity.section = section;
		entity.publicId = publicId;
		entity.sourceType = "CATEGORY";
		entity.categoryId = categoryId;
		entity.questionCount = questionCount;
		entity.difficultyCode = difficultyCode;
		entity.poolOrder = order;
		return entity;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getSourceType() {
		return sourceType;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public Integer getQuestionCount() {
		return questionCount;
	}

	public String getDifficultyCode() {
		return difficultyCode;
	}

	public Integer getPoolOrder() {
		return poolOrder;
	}
}
