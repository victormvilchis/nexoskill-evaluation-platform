package com.nexoskill.evaluation.organizations.interfaces.rest;

import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant-context")
public class TenantContextController {
    private final TenantContextResolver resolver;

    public TenantContextController(TenantContextResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public TenantContext current(HttpServletRequest request) {
        return resolver.resolve(request);
    }
}
