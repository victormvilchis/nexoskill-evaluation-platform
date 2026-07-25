package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.DuplicateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionHistory;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionPage;
import com.nexoskill.evaluation.questionbank.application.model.TransitionQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.UpdateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.service.CreateQuestionService;
import com.nexoskill.evaluation.questionbank.application.service.DuplicateQuestionService;
import com.nexoskill.evaluation.questionbank.application.service.GetQuestionHistoryService;
import com.nexoskill.evaluation.questionbank.application.service.GetQuestionService;
import com.nexoskill.evaluation.questionbank.application.service.QuestionWorkflowService;
import com.nexoskill.evaluation.questionbank.application.service.SearchQuestionsService;
import com.nexoskill.evaluation.questionbank.application.service.UpdateQuestionService;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/questions")
public class AdminQuestionController {

    private final SearchQuestionsService searchQuestionsService;
    private final GetQuestionService getQuestionService;
    private final CreateQuestionService createQuestionService;
    private final UpdateQuestionService updateQuestionService;
    private final QuestionWorkflowService workflowService;
    private final DuplicateQuestionService duplicateQuestionService;
    private final GetQuestionHistoryService historyService;

    public AdminQuestionController(
            SearchQuestionsService searchQuestionsService,
            GetQuestionService getQuestionService,
            CreateQuestionService createQuestionService,
            UpdateQuestionService updateQuestionService,
            QuestionWorkflowService workflowService,
            DuplicateQuestionService duplicateQuestionService,
            GetQuestionHistoryService historyService) {
        this.searchQuestionsService = searchQuestionsService;
        this.getQuestionService = getQuestionService;
        this.createQuestionService = createQuestionService;
        this.updateQuestionService = updateQuestionService;
        this.workflowService = workflowService;
        this.duplicateQuestionService = duplicateQuestionService;
        this.historyService = historyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public QuestionPage search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String difficultyCode,
            @RequestParam(required = false) String categoryPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchQuestionsService.search(
                query, status, typeCode, difficultyCode,
                categoryPublicId, page, size);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public QuestionDetail get(@PathVariable String publicId) {
        return getQuestionService.get(publicId);
    }

    @GetMapping("/{publicId}/versions")
    @PreAuthorize("hasAuthority('QUESTION_VERSION_VIEW')")
    public QuestionHistory versions(@PathVariable String publicId) {
        return historyService.get(publicId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUESTION_CREATE')")
    public ResponseEntity<QuestionDetail> create(
            @Valid @RequestBody CreateQuestionRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        QuestionDetail created = createQuestionService.create(
                new CreateQuestionCommand(
                        body.typeCode(), body.difficultyCode(),
                        body.categoryPublicId(), body.statement(), body.explanation(),
                        body.options().stream().map(option -> new QuestionOptionCommand(
                                option.text(), option.correct())).toList(),
                        actor.internalId(), ClientRequestInfo.ipAddress(request),
                        ClientRequestInfo.userAgent(request)
                ));
        return ResponseEntity.created(
                URI.create("/api/v1/admin/questions/" + created.publicId()))
                .body(created);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('QUESTION_UPDATE')")
    public QuestionDetail update(
            @PathVariable String publicId,
            @Valid @RequestBody UpdateQuestionRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return updateQuestionService.update(new UpdateQuestionCommand(
                publicId, body.typeCode(), body.difficultyCode(),
                body.categoryPublicId(), body.statement(), body.explanation(),
                body.changeSummary(),
                body.options().stream().map(option -> new QuestionOptionCommand(
                        option.text(), option.correct())).toList(),
                body.expectedEntityVersion(), actor.internalId(),
                ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)
        ));
    }

    @PostMapping("/{publicId}/transitions")
    @PreAuthorize("hasAnyAuthority('QUESTION_REVIEW','QUESTION_APPROVE','QUESTION_PUBLISH','QUESTION_ARCHIVE')")
    public QuestionDetail transition(
            @PathVariable String publicId,
            @Valid @RequestBody TransitionQuestionRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return workflowService.transition(new TransitionQuestionCommand(
                publicId, body.targetStatus(), body.expectedEntityVersion(),
                actor.internalId(), actor.permissions(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request)
        ));
    }

    @PostMapping("/{publicId}/duplicate")
    @PreAuthorize("hasAuthority('QUESTION_DUPLICATE')")
    public ResponseEntity<QuestionDetail> duplicate(
            @PathVariable String publicId,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        QuestionDetail duplicated = duplicateQuestionService.duplicate(
                new DuplicateQuestionCommand(
                        publicId, actor.internalId(),
                        ClientRequestInfo.ipAddress(request),
                        ClientRequestInfo.userAgent(request)
                ));
        return ResponseEntity.created(
                URI.create("/api/v1/admin/questions/" + duplicated.publicId()))
                .body(duplicated);
    }
}
