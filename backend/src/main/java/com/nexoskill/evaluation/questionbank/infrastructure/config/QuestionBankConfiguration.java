package com.nexoskill.evaluation.questionbank.infrastructure.config;

import com.nexoskill.evaluation.questionbank.application.port.out.QuestionUsageChecker;
import com.nexoskill.evaluation.questionbank.application.service.QuestionValidator;
import org.springframework.context.annotation.*;

@Configuration
public class QuestionBankConfiguration {
	@Bean
	QuestionValidator questionValidator() {
		return new QuestionValidator();
	}

	@Bean
	QuestionUsageChecker questionUsageChecker() {
		return id -> false;
	}
}
