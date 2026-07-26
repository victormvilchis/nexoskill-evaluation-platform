package com.nexoskill.evaluation.organizations.domain.model;

public record TenantContext(
        boolean globalAdministrator,
        boolean globalScope,
        Long organizationId,
        String organizationPublicId,
        String organizationCode) {

    public static TenantContext global(Long id, String publicId, String code) {
        return new TenantContext(true, true, id, publicId, code);
    }

    public static TenantContext organization(Long id, String publicId, String code, boolean administrator) {
        return new TenantContext(administrator, false, id, publicId, code);
    }

    public boolean hasOrganization() {
        return organizationId != null;
    }
}
