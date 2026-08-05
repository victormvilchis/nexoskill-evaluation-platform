package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.students.application.StudentImportService;
import com.nexoskill.evaluation.students.application.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/students/import")
public class StudentImportController {
    private final StudentImportService service;
    private final TenantContextResolver tenantResolver;

    public StudentImportController(StudentImportService service, TenantContextResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('STUDENT_IMPORT')")
    public StudentImportService.Preview preview(@RequestPart("file") MultipartFile file,
            @RequestParam(value = "organizationPublicId", required = false) String organizationPublicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        try {
            return service.preview(tenant(request), actor, organizationPublicId,
                    file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new BusinessException("STUDENT_IMPORT_FILE_READ",
                    "No fue posible leer el archivo seleccionado.");
        }
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('STUDENT_IMPORT')")
    public StudentImportService.ApplyResult apply(@Valid @RequestBody StudentImportService.ApplyCommand body,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return service.apply(tenant(request), actor, body,
                new StudentService.Actor(actor.internalId(), ClientRequestInfo.ipAddress(request),
                        ClientRequestInfo.userAgent(request)));
    }

    @DeleteMapping("/{token}")
    @PreAuthorize("hasAuthority('STUDENT_IMPORT')")
    public void discard(@PathVariable String token, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        service.discard(tenant(request), actor, token);
    }

    private TenantContext tenant(HttpServletRequest request) {
        return tenantResolver.resolve(request);
    }
}
