package com.nexoskill.evaluation.users.application.port.out;

import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Instant;
import java.util.List;

public interface UserManagementPort {

    boolean existsByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmailExcluding(
            String normalizedEmail,
            String excludedPublicId
    );

    AdminUserSummary create(NewUserData user);

    AdminUserPage search(
            String query,
            UserStatus status,
            int page,
            int size
    );

    ManagedUser getByPublicId(String publicId);

    AdminUserSummary updateProfile(
            String publicId,
            String email,
            String normalizedEmail,
            String firstName,
            String lastName,
            String displayName
    );

    AdminUserSummary updateAccess(
            String publicId,
            Instant startsAt,
            Instant expiresAt
    );

    AdminUserSummary updateRole(String publicId, String roleCode);

    AdminUserSummary updateStatus(
            String publicId,
            UserStatus userStatus,
            UserAccessStatus accessStatus,
            Instant changedAt
    );

    AdminUserSummary updatePassword(
            String publicId,
            String passwordHash,
            Instant temporaryPasswordExpiresAt
    );

    long countEffectiveAdministrators(Instant now);

    List<RoleOption> listActiveRoles();

    record NewUserData(
            String publicId,
            String email,
            String normalizedEmail,
            String passwordHash,
            String firstName,
            String lastName,
            String displayName,
            String roleCode,
            Instant startsAt,
            Instant expiresAt,
            Instant temporaryPasswordExpiresAt
    ) {
    }

    record ManagedUser(
            Long internalId,
            AdminUserSummary summary
    ) {
    }
}
