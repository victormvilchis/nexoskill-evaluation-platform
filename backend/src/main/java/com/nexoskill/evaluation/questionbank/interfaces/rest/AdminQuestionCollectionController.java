package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.service.CollectionService;
import com.nexoskill.evaluation.questionbank.domain.model.CollectionStatus;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/question-collections")
public class AdminQuestionCollectionController {
	private final CollectionService service;

	public AdminQuestionCollectionController(CollectionService s) {
		service = s;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
	public CollectionPage search(@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.search(query, status, page, size);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
	public CollectionDetail get(@PathVariable String id) {
		return service.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
	public ResponseEntity<CollectionDetail> create(@Valid @RequestBody CollectionRequests.Create b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		var c = service.create(new CollectionCommands.Create(b.name(), b.description(), b.categoryPublicIds(),
				b.questionPublicIds(), a.internalId()));
		return ResponseEntity.created(URI.create("/api/v1/admin/question-collections/" + c.publicId())).body(c);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
	public CollectionDetail update(@PathVariable String id, @Valid @RequestBody CollectionRequests.Update b,
			@AuthenticationPrincipal AuthenticatedUser a) {
		return service.update(new CollectionCommands.Update(id, b.name(), b.description(), b.categoryPublicIds(),
				b.questionPublicIds(), b.expectedEntityVersion(), a.internalId()));
	}

	@PostMapping("/{id}/{action}")
	@PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
	public CollectionDetail status(@PathVariable String id, @PathVariable String action,
			@Valid @RequestBody CollectionRequests.Status b, @AuthenticationPrincipal AuthenticatedUser a) {
		CollectionStatus target;
		if ("activate".equalsIgnoreCase(action))
			target = CollectionStatus.ACTIVE;
		else if ("deactivate".equalsIgnoreCase(action))
			target = CollectionStatus.INACTIVE;
		else
			throw new com.nexoskill.evaluation.shared.domain.BusinessException("COLLECTION_ACTION_INVALID",
					"La acción solicitada no es válida.");
		return service
				.status(new CollectionCommands.ChangeStatus(id, target, b.expectedEntityVersion(), a.internalId()));
	}
}
