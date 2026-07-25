package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionCatalogs;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListQuestionCatalogsService {

    private final QuestionCatalogPort catalogPort;

    public ListQuestionCatalogsService(QuestionCatalogPort catalogPort) {
        this.catalogPort = catalogPort;
    }

    @Transactional(readOnly = true)
    public QuestionCatalogs list() {
        return new QuestionCatalogs(
                catalogPort.listActiveTypes(),
                catalogPort.listActiveDifficulties(),
                catalogPort.listActiveCategories()
        );
    }
}
