package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetQuestionService {

    private final QuestionBankPort questionBankPort;

    public GetQuestionService(QuestionBankPort questionBankPort) {
        this.questionBankPort = questionBankPort;
    }

    @Transactional(readOnly = true)
    public QuestionDetail get(String publicId) {
        return questionBankPort.getByPublicId(PublicIdNormalizer.requiredUuid(
                publicId, "QUESTION_ID_REQUIRED", "La pregunta es obligatoria."
        ));
    }
}
