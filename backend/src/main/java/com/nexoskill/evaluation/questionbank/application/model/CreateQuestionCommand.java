package com.nexoskill.evaluation.questionbank.application.model;

import java.util.List;

public record CreateQuestionCommand(String typeCode, String difficultyCode, String technologyPublicId, String levelCode,
		List<String> categoryPublicIds, List<String> tags, String statement, String explanation,
		String promptMediaPublicId, String codeContent, QuestionAnswerSettings answerSettings,
		List<QuestionOptionCommand> options, String contentScope, String organizationPublicId, Long actorUserId) {
	public CreateQuestionCommand {
		categoryPublicIds = categoryPublicIds == null ? List.of() : List.copyOf(categoryPublicIds);
		tags = tags == null ? List.of() : List.copyOf(tags);
		options = options == null ? List.of() : List.copyOf(options);
		answerSettings = answerSettings == null ? QuestionAnswerSettings.empty() : answerSettings;
	}

	public CreateQuestionCommand(String typeCode, String difficultyCode, String technologyPublicId, String levelCode,
			List<String> categoryPublicIds, String statement, String explanation, String promptMediaPublicId,
			String codeContent, QuestionAnswerSettings answerSettings, List<QuestionOptionCommand> options,
			Long actorUserId) {
		this(typeCode, difficultyCode, technologyPublicId, levelCode, categoryPublicIds, List.of(), statement,
				explanation, promptMediaPublicId, codeContent, answerSettings, options, null, null, actorUserId);
	}

	public CreateQuestionCommand(String typeCode, List<String> categoryPublicIds, String statement, String explanation,
			String promptMediaPublicId, String codeContent, QuestionAnswerSettings answerSettings,
			List<QuestionOptionCommand> options, Long actorUserId) {
		this(typeCode, null, null, null, categoryPublicIds, List.of(), statement, explanation, promptMediaPublicId,
				codeContent, answerSettings, options, null, null, actorUserId);
	}
}
