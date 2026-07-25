package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.service.CatalogService;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/question-catalogs")
public class AdminQuestionCatalogController {
	private final CatalogService service;

	public AdminQuestionCatalogController(CatalogService s) {
		service = s;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('QUESTION_VIEW')")
	public QuestionCatalogs active() {
		return service.active();
	}

	@GetMapping("/categories")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public java.util.List<QuestionCategorySummary> categories() {
		return service.categories();
	}

	@PostMapping("/categories")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public ResponseEntity<QuestionCategorySummary> create(@Valid @RequestBody QuestionRequests.CreateCategory b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		return ResponseEntity
				.ok(service.create(new CategoryCommands.Create(b.code(), b.name(), b.description(), a.internalId())));
	}

	@PutMapping("/categories/{id}")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary update(@PathVariable String id,
			@Valid @RequestBody QuestionRequests.UpdateCategory b, @AuthenticationPrincipal AuthenticatedUser a) {
		return service.update(new CategoryCommands.Update(id, b.code(), b.name(), b.description(),
				b.expectedEntityVersion(), a.internalId()));
	}

	@PostMapping("/categories/{id}/{action}")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary status(@PathVariable String id, @PathVariable String action,
			@Valid @RequestBody QuestionRequests.Status b, @AuthenticationPrincipal AuthenticatedUser a) {
		CatalogStatus s;
		if ("activate".equalsIgnoreCase(action))
			s = CatalogStatus.ACTIVE;
		else if ("deactivate".equalsIgnoreCase(action))
			s = CatalogStatus.INACTIVE;
		else
			throw new com.nexoskill.evaluation.shared.domain.BusinessException("CATEGORY_ACTION_INVALID",
					"La acción solicitada no es válida.");
		return service.status(new CategoryCommands.ChangeStatus(id, s, b.expectedEntityVersion(), a.internalId()));
	}
}
