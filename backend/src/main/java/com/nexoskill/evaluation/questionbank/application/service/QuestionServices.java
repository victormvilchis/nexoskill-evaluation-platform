package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public final class QuestionServices {
    private QuestionServices() {
    }

    @Service
    public static class Create {
        private final QuestionBankPort port;
        private final QuestionValidator validator;

        public Create(QuestionBankPort port, QuestionValidator validator) {
            this.port = port;
            this.validator = validator;
        }

        @Transactional
        public QuestionDetail execute(CreateQuestionCommand command) {
            var type = parseType(command.typeCode());
            validator.validate(type, command.statement(), command.categoryPublicIds(), command.answerSettings(),
                    command.options());
            return port.create(command);
        }
    }

    @Service
    public static class Update {
        private final QuestionBankPort port;
        private final QuestionValidator validator;

        public Update(QuestionBankPort port, QuestionValidator validator) {
            this.port = port;
            this.validator = validator;
        }

        @Transactional
        public QuestionDetail execute(UpdateQuestionCommand command) {
            PublicIdNormalizer.requiredUuid(command.publicId(), "QUESTION_ID_INVALID",
                    "La pregunta indicada no es válida.");
            var type = parseType(command.typeCode());
            validator.validate(type, command.statement(), command.categoryPublicIds(), command.answerSettings(),
                    command.options());
            return port.update(command);
        }
    }

    @Service
    public static class Get {
        private final QuestionBankPort port;

        public Get(QuestionBankPort port) {
            this.port = port;
        }

        @Transactional(readOnly = true)
        public QuestionDetail execute(String id) {
            return port.get(id);
        }
    }

    @Service
    public static class Search {
        private final QuestionBankPort port;

        public Search(QuestionBankPort port) {
            this.port = port;
        }

        @Transactional(readOnly = true)
        public QuestionPage execute(String query, String statusValue, String type, String difficulty, String category,
                int page, int size) {
            QuestionStatus status = null;
            if (statusValue != null && !statusValue.isBlank()) {
                try {
                    status = QuestionStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
                } catch (Exception exception) {
                    throw new BusinessException("QUESTION_STATUS_INVALID", "El estado indicado no es válido.");
                }
            }
            return port.search(query, status, type, difficulty, category, Math.max(0, page),
                    Math.min(Math.max(size, 1), 100));
        }
    }

    @Service
    public static class Duplicate {
        private final QuestionBankPort port;

        public Duplicate(QuestionBankPort port) {
            this.port = port;
        }

        @Transactional
        public QuestionDetail execute(String id, Long actor) {
            return port.duplicate(id, actor);
        }
    }

    @Service
    public static class ChangeStatus {
        private final QuestionBankPort port;

        public ChangeStatus(QuestionBankPort port) {
            this.port = port;
        }

        @Transactional
        public QuestionDetail execute(String id, QuestionStatus status, long version, Long actor) {
            if (status != QuestionStatus.ACTIVE && status != QuestionStatus.ARCHIVED) {
                throw new BusinessException("QUESTION_STATUS_INVALID", "El estado solicitado no es válido.");
            }
            return port.changeStatus(id, status, version, actor);
        }
    }

    @Service
    public static class Delete {
        private final QuestionBankPort port;

        public Delete(QuestionBankPort port) {
            this.port = port;
        }

        @Transactional
        public QuestionDetail execute(String id, long version, String reason, Long actor) {
            String normalizedReason = reason == null || reason.isBlank()
                    ? "Eliminación lógica desde el panel administrativo."
                    : reason.trim();
            return port.softDelete(id, version, normalizedReason, actor);
        }
    }

    @Service
    public static class Restore {
        private final QuestionBankPort port;

        public Restore(QuestionBankPort port) {
            this.port = port;
        }

        @Transactional
        public QuestionDetail execute(String id, long version, Long actor) {
            return port.restore(id, version, actor);
        }
    }

    private static com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode parseType(String value) {
        try {
            return com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode
                    .valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new BusinessException("QUESTION_TYPE_INVALID", "El tipo de pregunta no es válido.");
        }
    }
}
