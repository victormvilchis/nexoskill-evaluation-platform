package com.nexoskill.evaluation.questionbank.infrastructure.config;

import com.nexoskill.evaluation.questionbank.application.service.QuestionDraftValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuestionBankConfiguration {

    @Bean
    QuestionDraftValidator questionDraftValidator() {
        return new QuestionDraftValidator();
    }
}
