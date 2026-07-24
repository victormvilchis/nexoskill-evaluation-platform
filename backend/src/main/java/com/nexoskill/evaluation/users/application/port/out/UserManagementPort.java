package com.nexoskill.evaluation.users.application.port.out;

import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Instant;
import java.util.List;

public interface UserManagementPort {

    boolean existsByNormalizedEmail(String normalizedEmail);

    AdminUserSummary create(NewUserData user);

    AdminUserPage search(
            String query,
            UserStatus status,
            int page,
            int size
    );

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
            Instant expiresAt
    ) {
    }
}
