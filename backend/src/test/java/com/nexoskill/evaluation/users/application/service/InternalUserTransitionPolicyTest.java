package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

class InternalUserTransitionPolicyTest {
	private final InternalUserTransitionPolicy policy = new InternalUserTransitionPolicy();

	@Test
	void deletedUsersCanOnlyBeRestoredAsInactive() {
		policy.validate(UserStatus.DELETED, UserStatus.INACTIVE);

		assertThatThrownBy(() -> policy.validate(UserStatus.DELETED, UserStatus.ACTIVE))
				.isInstanceOf(BusinessException.class).hasMessageContaining("no está permitida");
	}

	@Test
	void suspendedUsersCanReturnToInactive() {
		policy.validate(UserStatus.SUSPENDED, UserStatus.INACTIVE);
	}

	@Test
	void suspensionRequiresAReason() {
		assertThatThrownBy(() -> policy.normalizeReason("   ", true, null)).isInstanceOf(BusinessException.class)
				.hasMessage("El motivo de la acción es obligatorio.");
		assertThat(policy.normalizeReason("  Riesgo   de seguridad  ", true, null)).isEqualTo("Riesgo de seguridad");
	}
}
