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

	public AdminQuestionController(QuestionServices.Search s, QuestionServices.Get g, QuestionServices.Create c,
			QuestionServices.Update u, QuestionServices.Duplicate d, QuestionServices.ChangeStatus st) {
		search = s;
		get = g;
		create = c;
		update = u;
		duplicate = d;
		status = st;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('QUESTION_VIEW')")
	public QuestionPage search(@RequestParam(required = false) String query,
			@RequestParam(required = false) String status, @RequestParam(required = false) String typeCode,
			@RequestParam(required = false) String difficultyCode,
			@RequestParam(required = false) String categoryPublicId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return search.execute(query, status, typeCode, difficultyCode, categoryPublicId, page, size);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('QUESTION_VIEW')")
	public QuestionDetail get(@PathVariable String id) {
		return get.execute(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('QUESTION_CREATE')")
	public ResponseEntity<QuestionDetail> create(@Valid @RequestBody QuestionRequests.Create b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		var q = create.execute(new CreateQuestionCommand(b.typeCode(), b.difficultyCode(), b.categoryPublicIds(),
				b.statement(), b.explanation(), b.promptMediaPublicId(), b.codeLanguage(), b.codeContent(),
				settings(b.answerSettings()), options(b.options()), a.internalId()));
		return ResponseEntity.created(URI.create("/api/v1/admin/questions/" + q.publicId())).body(q);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('QUESTION_UPDATE')")
	public QuestionDetail update(@PathVariable String id, @Valid @RequestBody QuestionRequests.Update b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		return update.execute(new UpdateQuestionCommand(id, b.typeCode(), b.difficultyCode(), b.categoryPublicIds(),
				b.statement(), b.explanation(), b.promptMediaPublicId(), b.codeLanguage(), b.codeContent(),
				settings(b.answerSettings()), options(b.options()), b.expectedEntityVersion(), a.internalId()));
	}

	@PostMapping("/{id}/duplicate")
	@PreAuthorize("hasAuthority('QUESTION_DUPLICATE')")
	public ResponseEntity<QuestionDetail> duplicate(@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser a) {
		var q = duplicate.execute(id, a.internalId());
		return ResponseEntity.created(URI.create("/api/v1/admin/questions/" + q.publicId())).body(q);
	}

	@PostMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('QUESTION_ARCHIVE')")
	public QuestionDetail archive(@PathVariable String id, @RequestBody QuestionRequests.ChangeStatus b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		return status.execute(id, QuestionStatus.ARCHIVED, b.expectedEntityVersion(), a.internalId());
	}

	@PostMapping("/{id}/activate")
	@PreAuthorize("hasAuthority('QUESTION_UPDATE')")
	public QuestionDetail activate(@PathVariable String id, @RequestBody QuestionRequests.ChangeStatus b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		return status.execute(id, QuestionStatus.ACTIVE, b.expectedEntityVersion(), a.internalId());
	}

	private static java.util.List<QuestionOptionCommand> options(java.util.List<QuestionRequests.Option> o) {
		return o == null ? java.util.List.of()
				: o.stream().map(v -> new QuestionOptionCommand(v.text(), v.mediaPublicId(), v.correct())).toList();
	}

	private static QuestionAnswerSettings settings(QuestionRequests.AnswerSettings s) {
		return s == null ? QuestionAnswerSettings.empty()
				: new QuestionAnswerSettings(s.acceptedAnswers(), s.caseSensitive(), s.manualReview(), s.numericMin(),
						s.numericMax(), s.numericTolerance(), s.maxLength());
	}
}
