package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;

public interface QuestionBankPort {
    QuestionDetail create(CreateQuestionCommand command);
    QuestionDetail update(UpdateQuestionCommand command);
    QuestionDetail get(String publicId);
    QuestionPage search(QuestionSearchFilter filter, int page, int size);
    QuestionDetail duplicate(String publicId, Long actorUserId);
    QuestionDetail changeStatus(String publicId, QuestionStatus status, long expectedEntityVersion, Long actorUserId);
    QuestionDetail softDelete(String publicId, long expectedEntityVersion, String reason, Long actorUserId);
    QuestionDetail restore(String publicId, long expectedEntityVersion, Long actorUserId);
}
