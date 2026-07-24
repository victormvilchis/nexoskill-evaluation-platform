package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserPage;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchUsersService {

    private final UserManagementPort userManagementPort;

    public SearchUsersService(UserManagementPort userManagementPort) {
        this.userManagementPort = userManagementPort;
    }

    @Transactional(readOnly = true)
    public AdminUserPage search(
            String query,
            String status,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    "PAGINATION_INVALID",
                    "La paginación solicitada no es válida."
            );
        }

        UserStatus parsedStatus = parseStatus(status);
        String normalizedQuery = query == null || query.isBlank()
                ? null
                : query.trim().toLowerCase();

        return userManagementPort.search(
                normalizedQuery,
                parsedStatus,
                page,
                size
        );
    }

    private UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "USER_STATUS_INVALID",
                    "El estado de usuario indicado no es válido."
            );
        }
    }
}
