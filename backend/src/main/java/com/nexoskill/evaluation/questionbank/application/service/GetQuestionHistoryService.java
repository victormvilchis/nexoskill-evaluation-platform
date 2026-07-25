package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionHistory;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
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
        return new QuestionHistory(publicId, questionBankPort.getVersions(publicId));
    }
}
