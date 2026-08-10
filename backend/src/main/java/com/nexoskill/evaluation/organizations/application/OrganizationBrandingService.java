package com.nexoskill.evaluation.organizations.application;

import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationBrandingJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationBrandingRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrganizationBrandingService {
    private static final long MAX_LOGO_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final OrganizationRepository organizations;
    private final OrganizationBrandingRepository branding;
    private final Clock clock;

    public OrganizationBrandingService(OrganizationRepository organizations,
            OrganizationBrandingRepository branding, Clock clock) {
        this.organizations = organizations;
        this.branding = branding;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BrandView current(TenantContext tenant) {
        if (tenant == null || tenant.globalScope() || !tenant.hasOrganization()) {
            return BrandView.valtieris();
        }
        OrganizationJpaEntity organization = organizations.findById(tenant.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        return view(organization);
    }

    @Transactional(readOnly = true)
    public BrandView get(String publicId) {
        return view(customer(publicId));
    }

    @Transactional
    public BrandView upload(String publicId, MultipartFile file) {
        OrganizationJpaEntity organization = customer(publicId);
        validate(file);
        Instant now = clock.instant();
        try {
            byte[] bytes = file.getBytes();
            String contentType = file.getContentType().toLowerCase(Locale.ROOT);
            String fileName = safeFileName(file.getOriginalFilename());
            OrganizationBrandingJpaEntity entity = branding.findById(organization.getId())
                    .orElseGet(() -> OrganizationBrandingJpaEntity.create(organization.getId(), bytes, contentType, fileName, now));
            if (entity.getVersion() != null || branding.existsById(organization.getId())) {
                entity.replaceLogo(bytes, contentType, fileName, now);
            }
            branding.save(entity);
            return view(organization);
        } catch (IOException exception) {
            throw new BusinessException("ORGANIZATION_LOGO_READ_FAILED", "No fue posible leer la imagen seleccionada.");
        }
    }

    @Transactional(readOnly = true)
    public LogoContent logo(String publicId, TenantContext tenant) {
        OrganizationJpaEntity organization = customer(publicId);
        if (tenant == null || (!tenant.globalAdministrator()
                && (!tenant.hasOrganization() || !organization.getId().equals(tenant.organizationId())))) {
            throw new BusinessException("ORGANIZATION_LOGO_FORBIDDEN", "No tienes acceso a la identidad de esta organización.");
        }
        OrganizationBrandingJpaEntity value = branding.findById(organization.getId())
                .filter(item -> item.getLogoContent() != null)
                .orElseThrow(() -> new BusinessException("ORGANIZATION_LOGO_NOT_FOUND", "La organización no tiene una imagen configurada."));
        return new LogoContent(value.getLogoContent(), value.getLogoContentType(), value.getLogoFileName());
    }


    @Transactional(readOnly = true)
    public BrandView studentBrand(Long organizationId) {
        if (organizationId == null) {
            throw new BusinessException("STUDENT_ORGANIZATION_NOT_FOUND", "No fue posible determinar tu organización.");
        }
        OrganizationJpaEntity organization = organizations.findById(organizationId)
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        return branding.findById(organization.getId())
                .filter(item -> item.getLogoContent() != null)
                .map(item -> new BrandView(false, organization.getPublicId(), organization.getName(), true,
                        "/api/v1/student/development/branding/logo?v="
                                + (item.getLogoUpdatedAt() == null ? "0" : item.getLogoUpdatedAt().toEpochMilli())))
                .orElseGet(() -> new BrandView(false, organization.getPublicId(), organization.getName(), false, null));
    }

    @Transactional(readOnly = true)
    public LogoContent studentLogo(Long organizationId) {
        if (organizationId == null) {
            throw new BusinessException("STUDENT_ORGANIZATION_NOT_FOUND", "No fue posible determinar tu organización.");
        }
        OrganizationBrandingJpaEntity value = branding.findById(organizationId)
                .filter(item -> item.getLogoContent() != null)
                .orElseThrow(() -> new BusinessException("ORGANIZATION_LOGO_NOT_FOUND", "La organización no tiene una imagen configurada."));
        return new LogoContent(value.getLogoContent(), value.getLogoContentType(), value.getLogoFileName());
    }

    private BrandView view(OrganizationJpaEntity organization) {
        return branding.findById(organization.getId())
                .filter(item -> item.getLogoContent() != null)
                .map(item -> new BrandView(false, organization.getPublicId(), organization.getName(), true,
                        "/api/v1/organization-branding/" + organization.getPublicId() + "/logo?v="
                                + (item.getLogoUpdatedAt() == null ? "0" : item.getLogoUpdatedAt().toEpochMilli())))
                .orElseGet(() -> new BrandView(false, organization.getPublicId(), organization.getName(), false, null));
    }

    private OrganizationJpaEntity customer(String publicId) {
        OrganizationJpaEntity organization = organizations.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        if (organization.getOrganizationType() != OrganizationType.CUSTOMER || organization.isGlobal()) {
            throw new BusinessException("ORGANIZATION_BRANDING_GLOBAL_FORBIDDEN",
                    "La identidad de Valtieris se administra fuera del contexto de organizaciones comerciales.");
        }
        return organization;
    }

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("ORGANIZATION_LOGO_REQUIRED", "Selecciona una imagen para la organización.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BusinessException("ORGANIZATION_LOGO_TOO_LARGE", "La imagen no puede superar 2 MB.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("ORGANIZATION_LOGO_TYPE_INVALID", "Utiliza una imagen PNG, JPG o WEBP.");
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) return "organization-logo";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        normalized = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return normalized.length() <= 255 ? normalized : normalized.substring(normalized.length() - 255);
    }

    public record BrandView(boolean global, String organizationPublicId, String name, boolean hasLogo, String logoUrl) {
        public static BrandView valtieris() { return new BrandView(true, null, "Valtieris", true, null); }
    }
    public record LogoContent(byte[] content, String contentType, String fileName) {}
}
