package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCatalogPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListQuestionCategoriesService {

    private final QuestionCatalogPort catalogPort;

    public ListQuestionCategoriesService(QuestionCatalogPort catalogPort) {
        this.catalogPort = catalogPort;
    }

    @Transactional(readOnly = true)
    public List<QuestionCategorySummary> list() {
        return catalogPort.listCategories();
    }
}
