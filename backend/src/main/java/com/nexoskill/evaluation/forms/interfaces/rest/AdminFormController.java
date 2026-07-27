package com.nexoskill.evaluation.forms.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.forms.application.FormModels;
import com.nexoskill.evaluation.forms.application.FormService;
import com.nexoskill.evaluation.shared.interfaces.rest.PagedResponse;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private static final Map<String, String> ALLOWED_SORTS = Map.of(
            "title", "title", "code", "code", "status", "status",
            "createdAt", "createdAt", "updatedAt", "updatedAt");

    private final FormService service;

    public AdminFormController(FormService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FORM_VIEW')")
    public PagedResponse<FormModels.FormSummary> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        var pageable = PaginationParameters.of(page, size, sort, direction, ALLOWED_SORTS,
                "updatedAt", Sort.Direction.DESC, "id");
        return PagedResponse.from(service.list(query, status, mode, pageable), item -> item);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('FORM_VIEW')")
    public FormModels.FormDetail get(@PathVariable String publicId) {
        return service.get(publicId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FORM_CREATE')")
    public FormModels.FormDetail create(@RequestBody FormModels.FormCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(command, actor.internalId());
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public FormModels.FormDetail update(@PathVariable String publicId,
            @RequestBody FormModels.FormCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(publicId, command, actor.internalId());
    }

    @PostMapping("/{publicId}/status/{status}")
    @PreAuthorize("hasAuthority('FORM_STATUS_CHANGE')")
    public FormModels.FormDetail status(@PathVariable String publicId, @PathVariable String status,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.changeStatus(publicId, status, actor.internalId());
    }
}
