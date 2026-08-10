package com.nexoskill.evaluation.organizations.interfaces.rest;

import com.nexoskill.evaluation.organizations.application.OrganizationBrandingService;
import com.nexoskill.evaluation.organizations.application.OrganizationBrandingService.BrandView;
import com.nexoskill.evaluation.organizations.application.OrganizationBrandingService.LogoContent;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organization-branding")
public class OrganizationBrandingController {
    private final OrganizationBrandingService service;
    private final TenantContextResolver tenantResolver;

    public OrganizationBrandingController(OrganizationBrandingService service, TenantContextResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public BrandView current(HttpServletRequest request) {
        return service.current(tenantResolver.resolve(request));
    }

    @GetMapping("/admin/{publicId}")
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('ORGANIZATION_VIEW')")
    public BrandView get(@PathVariable String publicId) {
        return service.get(publicId);
    }

    @PostMapping(path = "/admin/{publicId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATOR') and hasAuthority('ORGANIZATION_UPDATE')")
    public BrandView upload(@PathVariable String publicId, @RequestParam("file") MultipartFile file) {
        return service.upload(publicId, file);
    }

    @GetMapping("/{publicId}/logo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> logo(@PathVariable String publicId, HttpServletRequest request) {
        LogoContent value = service.logo(publicId, tenantResolver.resolve(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(value.contentType()));
        headers.setContentDisposition(ContentDisposition.inline().filename(value.fileName()).build());
        headers.setCacheControl(CacheControl.noCache());
        return ResponseEntity.ok().headers(headers).body(value.content());
    }
}
