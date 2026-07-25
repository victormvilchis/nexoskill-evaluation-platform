package com.nexoskill.evaluation.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublicIdNormalizerTest {

	@Test
	void preservesCanonicalUuidWithLetters() {
		String value = "7a6ad962-20a8-4303-b98e-770997f48db8";

		assertThat(PublicIdNormalizer.requiredUuid(value, "REQUIRED", "Required")).isEqualTo(value);
	}

	@Test
	void canonicalizesUppercaseUuidWithoutChangingItsIdentity() {
		assertThat(PublicIdNormalizer.requiredUuid("7A6AD962-20A8-4303-B98E-770997F48DB8", "REQUIRED", "Required"))
				.isEqualTo("7a6ad962-20a8-4303-b98e-770997f48db8");
	}

	@Test
	void rejectsInvalidPublicId() {
		assertThatThrownBy(() -> PublicIdNormalizer.requiredUuid("not-a-uuid", "REQUIRED", "Required"))
				.isInstanceOf(BusinessException.class).extracting("code").isEqualTo("PUBLIC_ID_INVALID");
	}
}
