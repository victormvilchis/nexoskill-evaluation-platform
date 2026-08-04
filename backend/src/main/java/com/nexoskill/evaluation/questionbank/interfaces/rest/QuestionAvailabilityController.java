package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.questionbank.application.service.QuestionAvailabilityService;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/questions/{questionPublicId}/availability")
public class QuestionAvailabilityController {
	private final QuestionAvailabilityService service;
	private final TenantContextResolver tenantResolver;

	public QuestionAvailabilityController(QuestionAvailabilityService service, TenantContextResolver tenantResolver) {
		this.service = service;
		this.tenantResolver = tenantResolver;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('QUESTION_VIEW')")
	public QuestionAvailabilityService.AvailabilityView get(@PathVariable String questionPublicId,
			HttpServletRequest request) {
		return service.get(questionPublicId, tenantResolver.resolve(request));
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('QUESTION_UPDATE')")
	public QuestionAvailabilityService.AvailabilityView update(@PathVariable String questionPublicId,
			@Valid @RequestBody UpdateRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
			HttpServletRequest request) {
		return service.update(questionPublicId,
				new QuestionAvailabilityService.UpdateCommand(body.mode(), body.organizationPublicIds()),
				tenantResolver.resolve(request), new QuestionAvailabilityService.Actor(actor.internalId(),
						ClientRequestInfo.ipAddress(request), ClientRequestInfo.userAgent(request)));
	}

	public record UpdateRequest(@NotNull QuestionAvailabilityMode mode, List<String> organizationPublicIds) {
	}
}
