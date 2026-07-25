package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexoskill.evaluation.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void shouldAcceptStrongPassword() {
		assertThatCode(() -> policy.validate("NexoSkill#2026", "usuario@nexoskill.local")).doesNotThrowAnyException();
	}

	@Test
	void shouldRejectWeakPassword() {
		assertThatThrownBy(() -> policy.validate("debil123", "usuario@nexoskill.local"))
				.isInstanceOf(BusinessException.class).hasMessageContaining("mayúscula");
	}

	@Test
	void shouldRejectPasswordContainingEmailLocalPart() {
		assertThatThrownBy(() -> policy.validate("Usuario#2026A", "usuario@nexoskill.local"))
				.isInstanceOf(BusinessException.class).hasMessageContaining("correo");
	}
}
