package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.service.CatalogService;
import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/question-catalogs")
public class AdminQuestionCatalogController {
	private final CatalogService service;
	private final TenantContextResolver tenantContextResolver;

	public AdminQuestionCatalogController(CatalogService service, TenantContextResolver tenantContextResolver) {
		this.service = service;
		this.tenantContextResolver = tenantContextResolver;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('QUESTION_VIEW')")
	public QuestionCatalogs active(HttpServletRequest request) {
		return service.active(tenant(request));
	}

	@GetMapping("/categories")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public List<QuestionCategorySummary> categories(@RequestParam(defaultValue = "ACTIVE") String status,
			HttpServletRequest request) {
		return service.categories(tenant(request), parseStatus(status));
	}

	@GetMapping("/categories/{id}")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary get(@PathVariable String id, HttpServletRequest request) {
		return service.get(id, tenant(request));
	}

	@PostMapping("/categories")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public ResponseEntity<QuestionCategorySummary> create(@Valid @RequestBody QuestionRequests.CreateCategory body,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		QuestionCategorySummary created = service.create(new CategoryCommands.Create(body.code(), body.name(),
				body.description(), actor.internalId(), tenant(request)));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/categories/{id}")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary update(@PathVariable String id,
			@Valid @RequestBody QuestionRequests.UpdateCategory body, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return service.update(new CategoryCommands.Update(id, body.code(), body.name(), body.description(),
				body.expectedEntityVersion(), actor.internalId(), tenant(request)));
	}

	@PostMapping("/categories/{id}/activate")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary activate(@PathVariable String id,
			@Valid @RequestBody QuestionRequests.CategoryStatus body, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return service.status(new CategoryCommands.ChangeStatus(id, CatalogStatus.ACTIVE, body.expectedEntityVersion(),
				body.reason(), actor.internalId(), tenant(request)));
	}

	@PostMapping("/categories/{id}/deactivate")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary deactivate(@PathVariable String id,
			@Valid @RequestBody QuestionRequests.CategoryStatus body, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return service.status(new CategoryCommands.ChangeStatus(id, CatalogStatus.INACTIVE,
				body.expectedEntityVersion(), body.reason(), actor.internalId(), tenant(request)));
	}

	@DeleteMapping("/categories/{id}")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategorySummary delete(@PathVariable String id, @Valid @RequestBody QuestionRequests.Delete body,
			@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
		return service.delete(new CategoryCommands.Delete(id, body.expectedEntityVersion(), body.reason(),
				actor.internalId(), tenant(request)));
	}

	@GetMapping("/categories/{id}/dependencies")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public QuestionCategoryDependencies dependencies(@PathVariable String id, HttpServletRequest request) {
		return service.dependencies(id, tenant(request));
	}

	@GetMapping("/categories/{id}/status-history")
	@PreAuthorize("hasAuthority('QUESTION_CATEGORY_MANAGE')")
	public List<QuestionCategoryStatusHistory> history(@PathVariable String id, HttpServletRequest request) {
		return service.history(id, tenant(request));
	}

	private TenantContext tenant(HttpServletRequest request) {
		return tenantContextResolver.resolve(request);
	}

	private CatalogStatus parseStatus(String value) {
		if (value == null || value.isBlank() || "ACTIVE".equalsIgnoreCase(value))
			return CatalogStatus.ACTIVE;
		if ("ALL".equalsIgnoreCase(value))
			return null;
		try {
			return CatalogStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException("CATEGORY_STATUS_INVALID", "El estado indicado no es válido.");
		}
	}
}
