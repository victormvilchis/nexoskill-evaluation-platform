package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCategoryCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCatalogs;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.service.CreateQuestionCategoryService;
import com.nexoskill.evaluation.questionbank.application.service.ListQuestionCatalogsService;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/question-catalogs")
public class AdminQuestionCatalogController {

    private final ListQuestionCatalogsService listService;
    private final CreateQuestionCategoryService createCategoryService;

    public AdminQuestionCatalogController(
            ListQuestionCatalogsService listService,
            CreateQuestionCategoryService createCategoryService) {
        this.listService = listService;
        this.createCategoryService = createCategoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public QuestionCatalogs list() {
        return listService.list();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
    public ResponseEntity<QuestionCategorySummary> createCategory(
            @Valid @RequestBody CreateQuestionCategoryRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        QuestionCategorySummary created = createCategoryService.create(
                new CreateQuestionCategoryCommand(
                        body.code(),
                        body.name(),
                        body.description(),
                        actor.internalId(),
                        ClientRequestInfo.ipAddress(request),
                        ClientRequestInfo.userAgent(request)
                )
        );
        return ResponseEntity.ok(created);
    }
}
