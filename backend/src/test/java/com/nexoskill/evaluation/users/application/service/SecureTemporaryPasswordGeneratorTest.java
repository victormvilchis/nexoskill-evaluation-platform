package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureTemporaryPasswordGeneratorTest {
	private final SecureTemporaryPasswordGenerator generator = new SecureTemporaryPasswordGenerator();

	@Test
	void generatesTenCharacterPasswordsThatSatisfyTheRequiredPolicy() {
		for (int index = 0; index < 100; index++) {
			String password = generator.generate();
			assertThat(password).hasSize(10);
			assertThat(password.chars().anyMatch(Character::isUpperCase)).isTrue();
			assertThat(password.chars().anyMatch(Character::isLowerCase)).isTrue();
			assertThat(password.chars().anyMatch(Character::isDigit)).isTrue();
			assertThat(password.chars().anyMatch(value -> !Character.isLetterOrDigit(value))).isTrue();
		}
	}

	@Test
	void doesNotProduceAConstantOrPredictableSingleValue() {
		Set<String> generated = new HashSet<>();
		for (int index = 0; index < 50; index++) {
			generated.add(generator.generate());
		}
		assertThat(generated).hasSizeGreaterThan(45);
	}
}
