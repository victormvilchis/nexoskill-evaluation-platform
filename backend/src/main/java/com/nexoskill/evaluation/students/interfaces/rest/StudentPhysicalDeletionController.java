package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import com.nexoskill.evaluation.students.application.StudentPhysicalDeletionService;
import com.nexoskill.evaluation.students.application.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/permanent-deletions")
@PreAuthorize("hasAuthority('STUDENT_DELETE')")
public class StudentPhysicalDeletionController {
    private final StudentPhysicalDeletionService service;
    private final TenantContextResolver tenantResolver;

    public StudentPhysicalDeletionController(StudentPhysicalDeletionService service,
            TenantContextResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    public StudentPhysicalDeletionService.PageResult search(HttpServletRequest request,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") String module,
            @RequestParam(required = false) String organizationPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationParameters.validate(page, size);
        return service.search(tenantResolver.resolve(request), query, module, organizationPublicId, page, size);
    }

    @PostMapping("/{publicId}")
    public StudentPhysicalDeletionService.DeletionResult delete(@PathVariable String publicId,
            @Valid @RequestBody ConfirmRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return service.delete(tenantResolver.resolve(request), publicId, Boolean.TRUE.equals(body.confirmed()),
                new StudentService.Actor(user.internalId(), ClientRequestInfo.ipAddress(request),
                        ClientRequestInfo.userAgent(request)));
    }

    public record ConfirmRequest(@NotNull @AssertTrue Boolean confirmed) {}
}
