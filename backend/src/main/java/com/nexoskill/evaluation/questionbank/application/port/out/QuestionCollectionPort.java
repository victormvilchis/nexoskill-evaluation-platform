package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.*;

public interface QuestionCollectionPort {
	CollectionDetail create(CollectionCommands.Create command);

	CollectionDetail update(CollectionCommands.Update command);

	CollectionDetail get(String publicId);

	CollectionPage search(String query, String status, int page, int size);

	CollectionDetail changeStatus(CollectionCommands.ChangeStatus command);
}
