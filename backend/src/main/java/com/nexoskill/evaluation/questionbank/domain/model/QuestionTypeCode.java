package com.nexoskill.evaluation.questionbank.domain.model;

public enum QuestionTypeCode {
	SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, DROPDOWN, SHORT_TEXT, LONG_TEXT, NUMBER, CODE_RESPONSE;

	public boolean usesOptions() {
		return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE || this == TRUE_FALSE || this == DROPDOWN;
	}

	public boolean requiresManualReview() {
		return this == LONG_TEXT || this == CODE_RESPONSE;
	}
}
