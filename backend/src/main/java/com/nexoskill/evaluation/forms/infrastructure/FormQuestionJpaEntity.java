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
import java.math.BigDecimal;

@Entity
@Table(name = "FORM_QUESTION")
public class FormQuestionJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "FORM_QUESTION_ID")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "SECTION_ID", nullable = false)
	private FormSectionJpaEntity section;

	@Column(name = "QUESTION_ID", nullable = false)
	private Long questionId;

	@Column(name = "QUESTION_ORDER", nullable = false)
	private Integer questionOrder;

	@Column(name = "POINTS", nullable = false, precision = 8, scale = 2)
	private BigDecimal points;

	@Column(name = "REQUIRED", nullable = false)
	private Integer required;

	protected FormQuestionJpaEntity() {
	}

	static FormQuestionJpaEntity create(FormSectionJpaEntity section, Long questionId, int order, BigDecimal points,
			boolean required) {
		FormQuestionJpaEntity entity = new FormQuestionJpaEntity();
		entity.section = section;
		entity.questionId = questionId;
		entity.questionOrder = order;
		entity.points = points == null ? BigDecimal.ONE : points;
		entity.required = required ? 1 : 0;
		return entity;
	}

	public Long getQuestionId() {
		return questionId;
	}

	public Integer getQuestionOrder() {
		return questionOrder;
	}

	public BigDecimal getPoints() {
		return points;
	}

	public boolean isRequired() {
		return Integer.valueOf(1).equals(required);
	}
}
