package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionPage;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.util.List;

public interface QuestionBankPort {

    QuestionDetail create(NewQuestionData question);

    QuestionPage search(
            String query,
            QuestionStatus status,
            String typeCode,
            String difficultyCode,
            String categoryPublicId,
            int page,
            int size
    );

    QuestionDetail getByPublicId(String publicId);

    record NewQuestionData(
            String publicId,
            String typeCode,
            String difficultyCode,
            String categoryPublicId,
            String statement,
            String explanation,
            List<QuestionOptionCommand> options,
            Long createdBy
    ) {
        public NewQuestionData {
            options = List.copyOf(options);
        }
    }
}
