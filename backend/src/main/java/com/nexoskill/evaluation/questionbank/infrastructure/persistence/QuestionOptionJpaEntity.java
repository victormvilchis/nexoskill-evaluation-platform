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
	@Column(name = "IS_CORRECT", nullable = false)
	private Integer correct;
	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	protected QuestionOptionJpaEntity() {
	}

	public static QuestionOptionJpaEntity create(QuestionJpaEntity q, String id, int order, String text,
			QuestionMediaJpaEntity media, boolean correct, Instant now) {
		var e = new QuestionOptionJpaEntity();
		e.question = q;
		e.publicId = id;
		e.optionOrder = order;
		e.text = text;
		e.media = media;
		e.correct = correct ? 1 : 0;
		e.createdAt = now;
		return e;
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

	public boolean isCorrect() {
		return Integer.valueOf(1).equals(correct);
	}
}
