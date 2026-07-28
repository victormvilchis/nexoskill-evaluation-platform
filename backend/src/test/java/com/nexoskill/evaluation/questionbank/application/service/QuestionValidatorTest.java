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
						List.of(option("Java   Engineer", true), option(" java\tengineer ", false)), null));

		assertEquals("DUPLICATE_OPTION", exception.getCode());
	}

	@Test
	void rejectsDuplicateCategories() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> validator.validate(QuestionTypeCode.SINGLE_CHOICE, "Pregunta",
						List.of("category-id", "category-id"), QuestionAnswerSettings.empty(),
						List.of(option("A", true), option("B", false)), null));

		assertEquals("QUESTION_CATEGORY_DUPLICATED", exception.getCode());
	}

	@Test
	void rejectsMoreThanOneDistinctCategory() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> validator.validate(QuestionTypeCode.SINGLE_CHOICE, "Pregunta",
						List.of("category-a", "category-b"), QuestionAnswerSettings.empty(),
						List.of(option("A", true), option("B", false)), null));

		assertEquals("QUESTION_CATEGORY_SINGLE_REQUIRED", exception.getCode());
	}

	@Test
	void acceptsOpenTextWithManualReview() {
		assertDoesNotThrow(() -> validator.validate(QuestionTypeCode.OPEN_TEXT, "Explica", List.of("cat"),
				new QuestionAnswerSettings(List.of(), false, true, null, null, null, 1_000), List.of(), null));
	}

	@Test
	void matchingRequiresContentInBothColumns() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> validator.validate(QuestionTypeCode.MATCHING, "Relaciona", List.of("cat"),
						QuestionAnswerSettings.empty(),
						List.of(new QuestionOptionCommand("Clase", null, "", null, true, null),
								new QuestionOptionCommand("Método", null, "Acción", null, true, null)),
						null));

		assertEquals("QUESTION_MATCH_CONTENT_REQUIRED", exception.getCode());
	}

	@Test
	void acceptsMatchingPairs() {
		assertDoesNotThrow(
				() -> validator
						.validate(QuestionTypeCode.MATCHING, "Relaciona", List.of("cat"),
								QuestionAnswerSettings.empty(),
								List.of(new QuestionOptionCommand("Clase", null, "Plantilla", null, true, "Repasa POO"),
										new QuestionOptionCommand("Método", null, "Comportamiento", null, true, null)),
								null));
	}

	@Test
	void rejectsInvalidMaximumResponseLength() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> validator.validate(QuestionTypeCode.OPEN_TEXT, "Explica", List.of("cat"),
						new QuestionAnswerSettings(List.of(), false, true, null, null, null, 0), List.of(), null));

		assertEquals("QUESTION_RESPONSE_LENGTH_INVALID", exception.getCode());
	}

	private QuestionOptionCommand option(String text, boolean correct) {
		return new QuestionOptionCommand(text, null, null, null, correct, null);
	}
}
