package com.nexoskill.evaluation.questionbank.application.port.out;

public interface QuestionUsageChecker {
	boolean isUsedByActiveExam(Long questionId);
}
