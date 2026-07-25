package com.nexoskill.evaluation.questionbank.application.port.out;

import com.nexoskill.evaluation.questionbank.application.model.*;
import java.util.List;

public interface QuestionCatalogPort {
	QuestionCatalogs activeCatalogs();

	List<QuestionCategorySummary> categories();

	QuestionCategorySummary create(CategoryCommands.Create command);

	QuestionCategorySummary update(CategoryCommands.Update command);

	QuestionCategorySummary changeStatus(CategoryCommands.ChangeStatus command);
}
