package com.nexoskill.evaluation.collections.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.collections.application.LearningCollectionModels;
import com.nexoskill.evaluation.collections.application.LearningCollectionService;
import com.nexoskill.evaluation.shared.interfaces.rest.PagedResponse;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import java.util.List;
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
@RequestMapping("/api/v1/admin/collections")
public class AdminLearningCollectionController {

    private static final Map<String, String> ALLOWED_SORTS = Map.of(
            "name", "name",
            "code", "code",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final LearningCollectionService service;

    public AdminLearningCollectionController(LearningCollectionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COLLECTION_VIEW')")
    public PagedResponse<LearningCollectionModels.CollectionSummary> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        var pageable = PaginationParameters.of(page, size, sort, direction, ALLOWED_SORTS,
                "updatedAt", Sort.Direction.DESC, "id");
        return PagedResponse.from(service.list(query, status, pageable), item -> item);
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAuthority('COLLECTION_VIEW')")
    public List<LearningCollectionModels.FormOption> formOptions(
            @RequestParam(required = false) String query,
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
            @RequestBody LearningCollectionModels.CollectionCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(command, actor.internalId());
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
    public LearningCollectionModels.CollectionDetail update(
            @PathVariable String publicId,
            @RequestBody LearningCollectionModels.CollectionCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(publicId, command, actor.internalId());
    }

    @PostMapping("/{publicId}/status/{status}")
    @PreAuthorize("hasAuthority('COLLECTION_MANAGE')")
    public LearningCollectionModels.CollectionDetail changeStatus(
            @PathVariable String publicId,
            @PathVariable String status,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.changeStatus(publicId, status, actor.internalId());
    }
}
