package com.nexoskill.evaluation.forms.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "FORM_SECTION")
public class FormSectionJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "SECTION_ID")
	private Long id;

	@Column(name = "FORM_ID", nullable = false)
	private Long formId;

	@Column(name = "PUBLIC_ID", nullable = false, length = 36)
	private String publicId;

	@Column(name = "TITLE", nullable = false, length = 200)
	private String title;

	@Column(name = "DESCRIPTION", length = 1000)
	private String description;

	@Column(name = "SECTION_ORDER", nullable = false)
	private Integer sectionOrder;

	@OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("questionOrder ASC")
	private List<FormQuestionJpaEntity> questions = new ArrayList<>();

	@OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("poolOrder ASC")
	private List<FormQuestionPoolJpaEntity> pools = new ArrayList<>();

	protected FormSectionJpaEntity() {
	}

	public static FormSectionJpaEntity content(Long formId, String publicId) {
		FormSectionJpaEntity entity = new FormSectionJpaEntity();
		entity.formId = formId;
		entity.publicId = publicId;
		entity.title = "Contenido";
		entity.sectionOrder = 1;
		return entity;
	}

	public void addQuestion(Long questionId, int order, java.math.BigDecimal points, boolean required) {
		questions.add(FormQuestionJpaEntity.create(this, questionId, order, points, required));
	}

	public void addPool(String publicId, Long categoryId, int questionCount, String difficultyCode, int order) {
		pools.add(FormQuestionPoolJpaEntity.category(this, publicId, categoryId, questionCount, difficultyCode, order));
	}

	public Long getId() {
		return id;
	}

	public Long getFormId() {
		return formId;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Integer getSectionOrder() {
		return sectionOrder;
	}

	public List<FormQuestionJpaEntity> getQuestions() {
		return List.copyOf(questions);
	}

	public List<FormQuestionPoolJpaEntity> getPools() {
		return List.copyOf(pools);
	}
}
