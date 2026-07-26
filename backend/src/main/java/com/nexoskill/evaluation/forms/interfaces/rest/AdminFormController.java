package com.nexoskill.evaluation.forms.interfaces.rest;

import com.nexoskill.evaluation.forms.application.FormModels;
import com.nexoskill.evaluation.forms.application.FormService;
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
@RequestMapping("/api/v1/admin/forms")
public class AdminFormController {
	private final FormService service;

	public AdminFormController(FormService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('FORM_VIEW')")
	public List<FormModels.FormSummary> list(@RequestParam(defaultValue = "ACTIVE") String status) {
		return service.list(status);
	}

	@GetMapping("/{publicId}")
	@PreAuthorize("hasAuthority('FORM_VIEW')")
	public FormModels.FormDetail get(@PathVariable String publicId) {
		return service.get(publicId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FORM_CREATE')")
	public FormModels.FormDetail create(@RequestBody FormModels.FormCommand command) {
		return service.create(command);
	}

	@PutMapping("/{publicId}")
	@PreAuthorize("hasAuthority('FORM_UPDATE')")
	public FormModels.FormDetail update(@PathVariable String publicId, @RequestBody FormModels.FormCommand command) {
		return service.update(publicId, command);
	}

	@PostMapping("/{publicId}/status/{status}")
	@PreAuthorize("hasAuthority('FORM_STATUS_CHANGE')")
	public FormModels.FormDetail status(@PathVariable String publicId, @PathVariable String status) {
		return service.changeStatus(publicId, status);
	}
}
