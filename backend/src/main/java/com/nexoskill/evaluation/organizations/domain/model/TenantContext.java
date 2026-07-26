package com.nexoskill.evaluation.organizations.domain.model;

public record TenantContext(
        boolean globalAdministrator,
        Long organizationId,
        String organizationPublicId,
        String organizationCode) {

    public static TenantContext global() {
        return new TenantContext(true, null, null, null);
    }

    public static TenantContext organization(Long id, String publicId, String code, boolean admin) {
        return new TenantContext(admin, id, publicId, code);
    }

    public boolean hasOrganization() {
        return organizationId != null;
    }
}
