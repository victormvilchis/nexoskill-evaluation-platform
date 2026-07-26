package com.nexoskill.evaluation.collections.interfaces.rest;

import com.nexoskill.evaluation.collections.application.LearningCollectionModels;
import com.nexoskill.evaluation.collections.application.LearningCollectionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/collections")
public class AdminLearningCollectionController {

	private final LearningCollectionService service;

	public AdminLearningCollectionController(LearningCollectionService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
	public List<LearningCollectionModels.CollectionSummary> list(@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ACTIVE") String status) {
		return service.list(query, status);
	}

	@GetMapping("/form-options")
	@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
	public List<LearningCollectionModels.FormOption> formOptions(@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ACTIVE") String status) {
		return service.formOptions(query, status);
	}

	@GetMapping("/{publicId}")
	@PreAuthorize("hasAuthority('COLLECTION_VIEW')")
	public LearningCollectionModels.CollectionDetail get(@PathVariable String publicId) {
		return service.get(publicId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
	public LearningCollectionModels.CollectionDetail create(
			@RequestBody LearningCollectionModels.CollectionCommand command) {
		return service.create(command);
	}

	@PutMapping("/{publicId}")
	@PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
	public LearningCollectionModels.CollectionDetail update(@PathVariable String publicId,
			@RequestBody LearningCollectionModels.CollectionCommand command) {
		return service.update(publicId, command);
	}

	@PostMapping("/{publicId}/status/{status}")
	@PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
	public LearningCollectionModels.CollectionDetail changeStatus(@PathVariable String publicId,
			@PathVariable String status) {
		return service.changeStatus(publicId, status);
	}
}
