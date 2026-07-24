package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class UserManagementSupport {

    private UserManagementSupport() {
    }

    static void validateDates(Instant startsAt, Instant expiresAt) {
        if (startsAt == null) {
            throw new BusinessException(
                    "USER_ACCESS_START_REQUIRED",
                    "La fecha de inicio es obligatoria."
            );
        }
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new BusinessException(
                    "USER_ACCESS_DATES_INVALID",
                    "La fecha de vencimiento debe ser posterior a la fecha de inicio."
            );
        }
    }

    static void validatePassword(String password) {
        boolean valid = password != null
                && password.length() >= 10
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(value -> !Character.isLetterOrDigit(value));

        if (!valid) {
            throw new BusinessException(
                    "PASSWORD_POLICY_VIOLATION",
                    "La contraseña temporal debe tener al menos 10 caracteres, mayúscula, minúscula, número y símbolo."
            );
        }
    }

    static String displayName(
            String firstName,
            String lastName,
            String requestedDisplayName) {
        if (requestedDisplayName != null && !requestedDisplayName.isBlank()) {
            return requestedDisplayName.trim();
        }
        return firstName.trim() + " " + lastName.trim();
    }

    static Map<String, Object> auditData(
            AdminUserSummary before,
            AdminUserSummary after) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetUserPublicId", after.publicId());
        data.put("targetEmail", after.email());
        if (before != null) {
            data.put("before", snapshot(before));
        }
        data.put("after", snapshot(after));
        return data;
    }

    private static Map<String, Object> snapshot(AdminUserSummary user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", user.email());
        data.put("firstName", user.firstName());
        data.put("lastName", user.lastName());
        data.put("displayName", user.displayName());
        data.put("status", user.status().name());
        data.put("roles", user.roles());
        data.put("accessStatus", user.accessStatus().name());
        data.put("startsAt", user.startsAt().toString());
        data.put(
                "expiresAt",
                user.expiresAt() == null ? "NO_EXPIRATION" : user.expiresAt().toString()
        );
        return data;
    }
}
