package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.service.QuestionServices;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
public class AdminQuestionController {
    private final QuestionServices.Search search;
    private final QuestionServices.Get get;
    private final QuestionServices.Create create;
    private final QuestionServices.Update update;
    private final QuestionServices.Duplicate duplicate;
    private final QuestionServices.ChangeStatus status;
    private final QuestionServices.Delete delete;
    private final QuestionServices.Restore restore;
    private final com.nexoskill.evaluation.questionbank.application.service.QuestionGlobalCloneService globalClone;

    public AdminQuestionController(QuestionServices.Search search, QuestionServices.Get get,
            QuestionServices.Create create, QuestionServices.Update update, QuestionServices.Duplicate duplicate,
            QuestionServices.ChangeStatus status, QuestionServices.Delete delete, QuestionServices.Restore restore,
            com.nexoskill.evaluation.questionbank.application.service.QuestionGlobalCloneService globalClone) {
        this.search = search;
        this.get = get;
        this.create = create;
        this.update = update;
        this.duplicate = duplicate;
        this.status = status;
        this.delete = delete;
        this.restore = restore;
        this.globalClone = globalClone;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public QuestionPage search(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String categoryPublicId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String organizationPublicId,
            @RequestParam(required = false) String technologyPublicId,
            @RequestParam(required = false) String difficultyCode,
            @RequestParam(required = false) String levelCode,
            @RequestParam(required = false) String creatorPublicId,
            @RequestParam(required = false) java.time.LocalDate createdFrom,
            @RequestParam(required = false) java.time.LocalDate createdTo,
            @RequestParam(required = false) java.time.LocalDate updatedFrom,
            @RequestParam(required = false) java.time.LocalDate updatedTo,
            @RequestParam(required = false) Boolean clonedToGlobal,
            @RequestParam(required = false) Boolean inUse,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return search.execute(query, status, typeCode, categoryPublicId, scope, organizationPublicId,
                technologyPublicId, difficultyCode, levelCode, creatorPublicId, createdFrom, createdTo,
                updatedFrom, updatedTo, clonedToGlobal, inUse, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public QuestionDetail get(@PathVariable String id) {
        return get.execute(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUESTION_CREATE')")
    public ResponseEntity<QuestionDetail> create(@Valid @RequestBody QuestionRequests.Create body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        var question = create.execute(new CreateQuestionCommand(body.typeCode(), body.difficultyCode(),
                body.technologyPublicId(), body.levelCode(), body.categoryPublicIds(),
                body.statement(), body.explanation(), body.promptMediaPublicId(), body.codeContent(),
                settings(body.answerSettings()), options(body.options()), actor.internalId()));
        return ResponseEntity.created(URI.create("/api/v1/admin/questions/" + question.publicId())).body(question);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QUESTION_UPDATE')")
    public QuestionDetail update(@PathVariable String id, @Valid @RequestBody QuestionRequests.Update body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return update.execute(new UpdateQuestionCommand(id, body.typeCode(), body.difficultyCode(),
                body.technologyPublicId(), body.levelCode(), body.categoryPublicIds(),
                body.statement(), body.explanation(), body.promptMediaPublicId(), body.codeContent(),
                settings(body.answerSettings()), options(body.options()), body.expectedEntityVersion(),
                actor.internalId()));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('QUESTION_DUPLICATE')")
    public ResponseEntity<QuestionDetail> duplicate(@PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        var question = duplicate.execute(id, actor.internalId());
        return ResponseEntity.created(URI.create("/api/v1/admin/questions/" + question.publicId())).body(question);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('QUESTION_ARCHIVE')")
    public QuestionDetail archive(@PathVariable String id, @RequestBody QuestionRequests.ChangeStatus body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return status.execute(id, QuestionStatus.ARCHIVED, body.expectedEntityVersion(), actor.internalId());
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('QUESTION_UPDATE')")
    public QuestionDetail activate(@PathVariable String id, @RequestBody QuestionRequests.ChangeStatus body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return status.execute(id, QuestionStatus.ACTIVE, body.expectedEntityVersion(), actor.internalId());
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('QUESTION_ARCHIVE')")
    public QuestionDetail delete(@PathVariable String id, @Valid @RequestBody QuestionRequests.Delete body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return delete.execute(id, body.expectedEntityVersion(), body.reason(), actor.internalId());
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('QUESTION_UPDATE')")
    public QuestionDetail restore(@PathVariable String id, @RequestBody QuestionRequests.Status body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return restore.execute(id, body.expectedEntityVersion(), actor.internalId());
    }

    @GetMapping("/{id}/clone-to-global/preview")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PROMOTE')")
    public com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.PromotionPreview clonePreview(
            @PathVariable String id) {
        return globalClone.preview(id);
    }

    @PostMapping("/{id}/clone-to-global")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_PROMOTE')")
    public com.nexoskill.evaluation.questionbank.application.service.QuestionGlobalCloneService.CloneResult cloneToGlobal(
            @PathVariable String id,
            @RequestBody(required = false) QuestionRequests.CloneToGlobal body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        boolean includeDependencies = body == null || body.includeDependencies();
        return globalClone.cloneToGlobal(id, includeDependencies, body == null ? null : body.notes(),
                actor.internalId());
    }

    private static java.util.List<QuestionOptionCommand> options(java.util.List<QuestionRequests.Option> values) {
        return values == null ? java.util.List.of()
                : values.stream().map(value -> new QuestionOptionCommand(value.text(), value.mediaPublicId(),
                        value.matchText(), value.matchMediaPublicId(), value.correct(), value.feedback())).toList();
    }

    private static QuestionAnswerSettings settings(QuestionRequests.AnswerSettings value) {
        return value == null ? QuestionAnswerSettings.empty()
                : new QuestionAnswerSettings(value.acceptedAnswers(), value.caseSensitive(), value.manualReview(),
                        value.numericMin(), value.numericMax(), value.numericTolerance(), value.maxLength());
    }
}
