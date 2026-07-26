package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class InternalRolePolicy {
	public static final Set<String> ALLOWED_ROLES = Set.of("ADMINISTRATOR", "MANAGER", "SUPERVISOR");

	public String normalizeAndValidate(String roleCode) {
		String normalized = roleCode == null ? "" : roleCode.trim().toUpperCase();
		if (!ALLOWED_ROLES.contains(normalized)) {
			throw new BusinessException("INTERNAL_ROLE_INVALID", "El rol debe ser Administrador, Gestor o Supervisor.");
		}
		return normalized;
	}

	public void validateOrganization(String roleCode, String organizationPublicId) {
		if (("MANAGER".equals(roleCode) || "SUPERVISOR".equals(roleCode))
				&& (organizationPublicId == null || organizationPublicId.isBlank())) {
			throw new BusinessException("USER_ORGANIZATION_REQUIRED",
					"Los Gestores y Supervisores deben pertenecer a una organización.");
		}
	}
}
