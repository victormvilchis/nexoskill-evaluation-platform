package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.ChangeQuestionCategoryStatusCommand;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCategoryCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCatalogs;
import com.nexoskill.evaluation.questionbank.application.model.QuestionCategorySummary;
import com.nexoskill.evaluation.questionbank.application.service.ChangeQuestionCategoryStatusService;
import com.nexoskill.evaluation.questionbank.application.service.CreateQuestionCategoryService;
import com.nexoskill.evaluation.questionbank.application.service.ListQuestionCategoriesService;
import com.nexoskill.evaluation.questionbank.application.service.ListQuestionCatalogsService;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/question-catalogs")
public class AdminQuestionCatalogController {

    private final ListQuestionCatalogsService listService;
    private final CreateQuestionCategoryService createCategoryService;
    private final ListQuestionCategoriesService listCategoriesService;
    private final ChangeQuestionCategoryStatusService changeCategoryStatusService;

    public AdminQuestionCatalogController(
            ListQuestionCatalogsService listService,
            CreateQuestionCategoryService createCategoryService,
            ListQuestionCategoriesService listCategoriesService,
            ChangeQuestionCategoryStatusService changeCategoryStatusService) {
        this.listService = listService;
        this.createCategoryService = createCategoryService;
        this.listCategoriesService = listCategoriesService;
        this.changeCategoryStatusService = changeCategoryStatusService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public QuestionCatalogs list() {
        return listService.list();
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
    public java.util.List<QuestionCategorySummary> listCategories() {
        return listCategoriesService.list();
    }

    @PostMapping("/categories/{publicId}/activate")
    @PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
    public QuestionCategorySummary activateCategory(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return changeCategoryStatusService.change(new ChangeQuestionCategoryStatusCommand(
                publicId, CatalogStatus.ACTIVE, actor.internalId(),
                ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)
        ));
    }

    @PostMapping("/categories/{publicId}/deactivate")
    @PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
    public QuestionCategorySummary deactivateCategory(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return changeCategoryStatusService.change(new ChangeQuestionCategoryStatusCommand(
                publicId, CatalogStatus.INACTIVE, actor.internalId(),
                ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)
        ));
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
