package com.nexoskill.evaluation.organizations.application;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.UserOrganizationMembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantContextResolver {
    public static final String CONTEXT_HEADER = "X-Organization-Context";

    private final UserOrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;

    public TenantContextResolver(UserOrganizationMembershipRepository membershipRepository,
                                 OrganizationRepository organizationRepository) {
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
    }

    public TenantContext resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("No existe una identidad autenticada.");
        }

        boolean administrator = user.roles().stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(role -> role.equals("ADMINISTRATOR") || role.equals("ADMIN"));

        if (administrator) {
            String selected = request.getHeader(CONTEXT_HEADER);
            if (selected == null || selected.isBlank()) return TenantContext.global();
            OrganizationJpaEntity organization = organizationRepository.findByPublicId(selected.trim())
                    .orElseThrow(() -> new AccessDeniedException("El contexto de organización no existe."));
            validateOperational(organization);
            return TenantContext.organization(organization.getId(), organization.getPublicId(), organization.getCode(), true);
        }

        OrganizationJpaEntity organization = membershipRepository.findActiveOrganizationForUser(user.internalId())
                .orElseThrow(() -> new AccessDeniedException("El usuario no tiene una organización activa asignada."));
        validateOperational(organization);
        return TenantContext.organization(organization.getId(), organization.getPublicId(), organization.getCode(), false);
    }

    private void validateOperational(OrganizationJpaEntity organization) {
        if (!organization.isOperational(LocalDate.now())) {
            throw new AccessDeniedException("La organización está inactiva o fuera de vigencia.");
        }
    }
}
