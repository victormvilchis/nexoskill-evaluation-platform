package com.nexoskill.evaluation.questionbank.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nexoskill.evaluation.questionbank.application.model.QuestionAnswerSettings;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionValidatorTest {

	private final QuestionValidator validator = new QuestionValidator();

	@Test
	void rejectsDuplicateOptionsIgnoringCaseAndWhitespace() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> validator.validate(QuestionTypeCode.SINGLE_CHOICE, "Pregunta", List.of("cat"),
						QuestionAnswerSettings.empty(),
						List.of(new QuestionOptionCommand("Java   Engineer", null, true),
								new QuestionOptionCommand(" java\tengineer ", null, false))));

		assertEquals("DUPLICATE_OPTION", exception.getCode());
	}

	@Test
	void rejectsDuplicateCategories() {
		BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate(
				QuestionTypeCode.SINGLE_CHOICE, "Pregunta", List.of("category-id", "category-id"),
				QuestionAnswerSettings.empty(),
				List.of(new QuestionOptionCommand("A", null, true), new QuestionOptionCommand("B", null, false))));

		assertEquals("QUESTION_CATEGORY_DUPLICATED", exception.getCode());
	}

	@Test
	void acceptsManualLongText() {
		assertDoesNotThrow(() -> validator.validate(QuestionTypeCode.LONG_TEXT, "Explica", List.of("cat"),
				new QuestionAnswerSettings(List.of(), false, true, null, null, null, 1_000), List.of()));
	}

	@Test
	void shortTextNeedsAnswerOrReview() {
		assertThrows(BusinessException.class, () -> validator.validate(QuestionTypeCode.SHORT_TEXT, "Nombre",
				List.of("cat"), QuestionAnswerSettings.empty(), List.of()));
	}

	@Test
	void rejectsInvalidMaximumResponseLength() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> validator.validate(QuestionTypeCode.LONG_TEXT, "Explica", List.of("cat"),
						new QuestionAnswerSettings(List.of(), false, true, null, null, null, 0), List.of()));

		assertEquals("QUESTION_RESPONSE_LENGTH_INVALID", exception.getCode());
	}
}
