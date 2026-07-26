package com.nexoskill.evaluation.organizations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LicensePolicyTest {
	@Test
	void recommendsTwentyPercentWithMinimumOne() {
		assertEquals(1, LicensePolicy.recommendedIncludedReplacements(1));
		assertEquals(2, LicensePolicy.recommendedIncludedReplacements(10));
		assertEquals(5, LicensePolicy.recommendedIncludedReplacements(25));
		assertEquals(20, LicensePolicy.recommendedIncludedReplacements(100));
	}

	@Test
	void addsIncludedAndAdditionalReplacementCapacity() {
		LicensePolicy policy = new LicensePolicy(25, 5, 2, 24, 7, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
		assertEquals(7, policy.totalReplacementCapacity());
	}
}
