package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionPage;
import com.nexoskill.evaluation.questionbank.application.model.QuestionVersionSummary;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.util.List;

public interface QuestionBankPort {

    QuestionDetail create(NewQuestionData question);

    UpdateResult update(UpdateQuestionData question);

    TransitionResult transition(
            String publicId,
            QuestionStatus targetStatus,
            long expectedEntityVersion,
            Long actorUserId
    );

    QuestionDetail duplicate(
            String sourcePublicId,
            String newPublicId,
            Long actorUserId
    );

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

    List<QuestionVersionSummary> getVersions(String publicId);

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

    record UpdateQuestionData(
            String publicId,
            String typeCode,
            String difficultyCode,
            String categoryPublicId,
            String statement,
            String explanation,
            String changeSummary,
            List<QuestionOptionCommand> options,
            long expectedEntityVersion,
            Long updatedBy
    ) {
        public UpdateQuestionData {
            options = List.copyOf(options);
        }
    }

    record UpdateResult(
            QuestionDetail question,
            int previousVersionNumber,
            boolean createdNewVersion
    ) {
    }

    record TransitionResult(
            QuestionDetail question,
            QuestionStatus previousStatus,
            QuestionStatus currentStatus
    ) {
    }
}
