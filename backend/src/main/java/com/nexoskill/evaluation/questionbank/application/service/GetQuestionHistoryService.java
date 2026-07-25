package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionHistory;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetQuestionHistoryService {

    private final QuestionBankPort questionBankPort;

    public GetQuestionHistoryService(QuestionBankPort questionBankPort) {
        this.questionBankPort = questionBankPort;
    }

    @Transactional(readOnly = true)
    public QuestionHistory get(String publicId) {
        String normalized = PublicIdNormalizer.requiredUuid(
                publicId, "QUESTION_ID_REQUIRED", "La pregunta es obligatoria."
        );
        return new QuestionHistory(normalized, questionBankPort.getVersions(normalized));
    }
}
