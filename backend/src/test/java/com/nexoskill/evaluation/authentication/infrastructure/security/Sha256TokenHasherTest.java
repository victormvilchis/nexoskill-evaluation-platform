package com.nexoskill.evaluation.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256TokenHasherTest {

	private final Sha256TokenHasher hasher = new Sha256TokenHasher();

	@Test
	void shouldCreateStableHexadecimalHash() {
		String first = hasher.hash("session-token");
		String second = hasher.hash("session-token");

		assertThat(first).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(second);
	}

	@Test
	void shouldNotExposeRawToken() {
		assertThat(hasher.hash("secret-token")).doesNotContain("secret-token");
	}
}
