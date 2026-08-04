package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.questionbank.application.model.QuestionTagView;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/question-tags")
public class AdminQuestionTagController {
	private final QuestionBankPort questionBank;

	public AdminQuestionTagController(QuestionBankPort questionBank) {
		this.questionBank = questionBank;
	}

	@GetMapping("/suggestions")
	@PreAuthorize("hasAuthority('QUESTION_VIEW')")
	public List<QuestionTagView> suggestions(@RequestParam(defaultValue = "") String query,
			@RequestParam(defaultValue = "8") int limit) {
		return questionBank.suggestTags(query, Math.min(Math.max(limit, 1), 10));
	}
}
