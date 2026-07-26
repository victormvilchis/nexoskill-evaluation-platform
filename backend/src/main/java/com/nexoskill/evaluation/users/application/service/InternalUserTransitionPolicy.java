package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class InternalUserTransitionPolicy {
	private static final Map<UserStatus, Set<UserStatus>> ALLOWED = Map.of(UserStatus.ACTIVE,
			Set.of(UserStatus.INACTIVE, UserStatus.SUSPENDED, UserStatus.DELETED), UserStatus.INACTIVE,
			Set.of(UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.DELETED), UserStatus.SUSPENDED,
			Set.of(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.DELETED), UserStatus.DELETED,
			Set.of(UserStatus.INACTIVE));

	public void validate(UserStatus current, UserStatus next) {
		if (!ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
			throw new BusinessException("USER_STATUS_TRANSITION_INVALID",
					"La transición de " + current + " a " + next + " no está permitida.");
		}
	}

	public String normalizeReason(String reason, boolean required, String defaultReason) {
		if (reason == null || reason.isBlank()) {
			if (required) {
				throw new BusinessException("USER_STATUS_REASON_REQUIRED", "El motivo de la acción es obligatorio.");
			}
			return defaultReason;
		}
		String normalized = reason.trim().replaceAll("\\s+", " ");
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
	}
}
