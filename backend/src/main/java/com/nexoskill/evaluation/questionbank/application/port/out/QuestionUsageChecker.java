package com.nexoskill.evaluation.questionbank.application.port.out;

public interface QuestionUsageChecker {
    boolean isUsedByActiveExam(Long questionId);

    DetachmentResult detachFromForms(Long questionId);

    void deletePermanently(Long questionId);

    record DetachmentResult(int fixedFormRelations, int collectionRelations) {
        public int totalDirectRelations() {
            return fixedFormRelations + collectionRelations;
        }
    }
}
