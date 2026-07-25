package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record UpdateQuestionCommand(String publicId, String typeCode, String difficultyCode,
		List<String> categoryPublicIds, String statement, String explanation, String promptMediaPublicId,
		String codeLanguage, String codeContent, QuestionAnswerSettings answerSettings,
		List<QuestionOptionCommand> options, long expectedEntityVersion, Long actorUserId) {
	public UpdateQuestionCommand {
		categoryPublicIds = categoryPublicIds == null ? List.of() : List.copyOf(categoryPublicIds);
		options = options == null ? List.of() : List.copyOf(options);
		answerSettings = answerSettings == null ? QuestionAnswerSettings.empty() : answerSettings;
	}
}
