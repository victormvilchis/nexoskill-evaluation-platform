package com.nexoskill.evaluation.forms.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "EVALUATION_FORM")
public class FormJpaEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "FORM_ID")
	public Long id;
	@Column(name = "PUBLIC_ID", nullable = false, length = 36)
	public String publicId;
	@Column(name = "FORM_CODE", nullable = false, length = 80)
	public String code;
	@Column(name = "TITLE", nullable = false, length = 200)
	public String title;
	@Column(name = "DESCRIPTION", length = 2000)
	public String description;
	@Column(name = "STATUS", nullable = false, length = 20)
	public String status;
	@Column(name = "MODE_CODE", nullable = false, length = 20)
	public String modeCode;
	@Column(name = "PASSING_SCORE", nullable = false)
	public BigDecimal passingScore;
	@Column(name = "MAX_ATTEMPTS")
	public Integer maxAttempts;
	@Column(name = "RETRY_UNTIL_PASSED", nullable = false)
	public Integer retryUntilPassed;
	@Column(name = "ACCEPT_RESPONSES", nullable = false)
	public Integer acceptResponses;
	@Column(name = "STARTS_AT")
	public OffsetDateTime startsAt;
	@Column(name = "ENDS_AT")
	public OffsetDateTime endsAt;
	@Column(name = "DURATION_MINUTES")
	public Integer durationMinutes;
	@Column(name = "SHOW_RESULTS", nullable = false)
	public Integer showResults;
	@Column(name = "SHOW_CORRECT_ANSWERS", nullable = false)
	public Integer showCorrectAnswers;
	@Column(name = "RANDOMIZE_QUESTIONS", nullable = false)
	public Integer randomizeQuestions;
	@Column(name = "RANDOMIZE_OPTIONS", nullable = false)
	public Integer randomizeOptions;
	@Column(name = "SHOW_PROGRESS", nullable = false)
	public Integer showProgress;
	@Column(name = "HIDE_QUESTION_NUMBERS", nullable = false)
	public Integer hideQuestionNumbers;
	@Column(name = "ALLOW_SAVE_RESUME", nullable = false)
	public Integer allowSaveResume;
	@Column(name = "ONE_ACTIVE_ATTEMPT", nullable = false)
	public Integer oneActiveAttempt;
	@Column(name = "THANK_YOU_MESSAGE", length = 2000)
	public String thankYouMessage;
	@Column(name = "CREATED_AT", nullable = false)
	public OffsetDateTime createdAt;
	@Column(name = "UPDATED_AT", nullable = false)
	public OffsetDateTime updatedAt;
	@Version
	@Column(name = "VERSION_NO", nullable = false)
	public Long version;

	public FormJpaEntity() {
	}
}
