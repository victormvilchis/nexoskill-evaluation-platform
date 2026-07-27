package com.nexoskill.evaluation.globalcontent.interfaces.rest;

import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.GrantView;
import com.nexoskill.evaluation.globalcontent.application.service.OrganizationContentGrantService;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/global-content")
public class OrganizationGlobalContentController {
    private final OrganizationContentGrantService grants;
    private final TenantContextResolver tenantContextResolver;

    public OrganizationGlobalContentController(OrganizationContentGrantService grants,
            TenantContextResolver tenantContextResolver) {
        this.grants = grants;
        this.tenantContextResolver = tenantContextResolver;
    }

    @GetMapping("/available")
    @PreAuthorize("hasAuthority('GLOBAL_CONTENT_USE')")
    public List<GrantView> available(HttpServletRequest request) {
        var tenant = tenantContextResolver.resolve(request);
        if (tenant.globalScope()) {
            return List.of();
        }
        return grants.list(tenant.organizationPublicId());
    }
}
