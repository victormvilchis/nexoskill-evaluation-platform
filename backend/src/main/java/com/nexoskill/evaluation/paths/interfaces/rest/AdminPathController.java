package com.nexoskill.evaluation.paths.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.paths.application.PathModels;
import com.nexoskill.evaluation.paths.application.PathService;
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
@RequestMapping("/api/v1/admin/paths")
public class AdminPathController {
    private static final Map<String, String> ALLOWED_SORTS = Map.of("name", "name", "code", "code",
            "status", "status", "createdAt", "createdAt", "updatedAt", "updatedAt");

    private final PathService service;

    public AdminPathController(PathService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('PATH_VIEW')")
    public PagedResponse<PathModels.PathSummary> list(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        var pageable = PaginationParameters.of(page, size, sort, direction, ALLOWED_SORTS, "updatedAt",
                Sort.Direction.DESC, "id");
        return PagedResponse.from(service.list(query, status, pageable), item -> item);
    }

    @GetMapping("/collection-options")
    @PreAuthorize("hasAuthority('PATH_VIEW')")
    public List<PathModels.CollectionOption> collectionOptions(@RequestParam(required = false) String query) {
        return service.collectionOptions(query);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('PATH_VIEW')")
    public PathModels.PathDetail get(@PathVariable String publicId) { return service.get(publicId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PATH_MANAGE')")
    public PathModels.PathDetail create(@RequestBody PathModels.PathCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(command, actor);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAuthority('PATH_MANAGE')")
    public PathModels.PathDetail update(@PathVariable String publicId, @RequestBody PathModels.PathCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(publicId, command, actor);
    }

    @PostMapping("/{publicId}/status/{status}")
    @PreAuthorize("hasAuthority('PATH_MANAGE')")
    public PathModels.PathDetail changeStatus(@PathVariable String publicId, @PathVariable String status,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.changeStatus(publicId, status, actor);
    }
}
