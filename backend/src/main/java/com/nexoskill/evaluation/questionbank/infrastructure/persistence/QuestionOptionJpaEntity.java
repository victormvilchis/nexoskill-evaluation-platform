package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "QUESTION_OPTION")
public class QuestionOptionJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "QUESTION_OPTION_ID")
	private Long id;

	@Column(name = "PUBLIC_ID", nullable = false, unique = true, length = 36)
	private String publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "QUESTION_ID", nullable = false)
	private QuestionJpaEntity question;

	@Column(name = "QUESTION_VERSION_ID", insertable = false, updatable = false)
	private Long legacyVersionId;

	@Column(name = "OPTION_ORDER", nullable = false)
	private int optionOrder;

	@Lob
	@Column(name = "OPTION_TEXT")
	private String text;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "MEDIA_ID")
	private QuestionMediaJpaEntity media;

	@Lob
	@Column(name = "MATCH_TEXT")
	private String matchText;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "MATCH_MEDIA_ID")
	private QuestionMediaJpaEntity matchMedia;

	@Column(name = "IS_CORRECT", nullable = false)
	private Integer correct;

	@Lob
	@Column(name = "FEEDBACK_TEXT")
	private String feedback;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	protected QuestionOptionJpaEntity() {
	}

	public static QuestionOptionJpaEntity create(QuestionJpaEntity question, String publicId, int order, String text,
			QuestionMediaJpaEntity media, String matchText, QuestionMediaJpaEntity matchMedia, boolean correct,
			String feedback, Instant now) {
		var entity = new QuestionOptionJpaEntity();
		entity.question = question;
		entity.publicId = publicId;
		entity.optionOrder = order;
		entity.text = text;
		entity.media = media;
		entity.matchText = matchText;
		entity.matchMedia = matchMedia;
		entity.correct = correct ? 1 : 0;
		entity.feedback = feedback;
		entity.createdAt = now;
		return entity;
	}

	public String getPublicId() {
		return publicId;
	}

	public int getOptionOrder() {
		return optionOrder;
	}

	public String getText() {
		return text;
	}

	public QuestionMediaJpaEntity getMedia() {
		return media;
	}

	public String getMatchText() {
		return matchText;
	}

	public QuestionMediaJpaEntity getMatchMedia() {
		return matchMedia;
	}

	public boolean isCorrect() {
		return Integer.valueOf(1).equals(correct);
	}

	public String getFeedback() {
		return feedback;
	}
}
