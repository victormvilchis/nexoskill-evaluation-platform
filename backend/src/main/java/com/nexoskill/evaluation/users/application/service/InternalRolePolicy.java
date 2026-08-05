package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.infrastructure.persistence.SpringDataRoleJpaRepository;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InternalRolePolicy {

    private final SpringDataRoleJpaRepository roles;

    public InternalRolePolicy(SpringDataRoleJpaRepository roles) {
        this.roles = roles;
    }

    public String normalizeAndValidate(String roleCode) {
        String normalized = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        return roles.findByCodeIgnoreCase(normalized)
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .filter(role -> !"USER".equals(role.getCode()))
                .map(role -> role.getCode())
                .orElseThrow(() -> new BusinessException("INTERNAL_ROLE_INVALID",
                        "El rol seleccionado no existe, está inactivo o no corresponde a un usuario interno."));
    }

    public void validateOrganization(String roleCode, String organizationPublicId) {
        if (!"ADMINISTRATOR".equals(roleCode)
                && (organizationPublicId == null || organizationPublicId.isBlank())) {
            throw new BusinessException("USER_ORGANIZATION_REQUIRED",
                    "Los roles organizacionales deben pertenecer a una organización comercial.");
        }
    }
}
