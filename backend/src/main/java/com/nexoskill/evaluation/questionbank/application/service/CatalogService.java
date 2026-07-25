package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
	private final QuestionCatalogPort port;

	public CatalogService(QuestionCatalogPort p) {
		port = p;
	}

	@Transactional(readOnly = true)
	public QuestionCatalogs active() {
		return port.activeCatalogs();
	}

	@Transactional(readOnly = true)
	public java.util.List<QuestionCategorySummary> categories() {
		return port.categories();
	}

	@Transactional
	public QuestionCategorySummary create(CategoryCommands.Create c) {
		return port.create(c);
	}

	@Transactional
	public QuestionCategorySummary update(CategoryCommands.Update c) {
		return port.update(c);
	}

	@Transactional
	public QuestionCategorySummary status(CategoryCommands.ChangeStatus c) {
		return port.changeStatus(c);
	}
}
