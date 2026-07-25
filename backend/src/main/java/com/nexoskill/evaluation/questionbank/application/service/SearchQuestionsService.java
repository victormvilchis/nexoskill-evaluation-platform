package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionPage;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchQuestionsService {

    private final QuestionBankPort questionBankPort;

    public SearchQuestionsService(QuestionBankPort questionBankPort) {
        this.questionBankPort = questionBankPort;
    }

    @Transactional(readOnly = true)
    public QuestionPage search(
            String query,
            String status,
            String typeCode,
            String difficultyCode,
            String categoryPublicId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    "PAGINATION_INVALID",
                    "La paginación solicitada no es válida."
            );
        }

        return questionBankPort.search(
                normalizeQuery(query),
                parseStatus(status),
                normalizeCode(typeCode),
                normalizeCode(difficultyCode),
                normalizeOptional(categoryPublicId),
                page,
                size
        );
    }

    private QuestionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuestionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "QUESTION_STATUS_INVALID",
                    "El estado de pregunta indicado no es válido."
            );
        }
    }

    private String normalizeQuery(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
