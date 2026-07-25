package com.nexoskill.evaluation.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccessTest {

	private static final Instant NOW = Instant.parse("2026-07-24T18:00:00Z");

	@Test
	void shouldAllowAccessInsideActiveWindow() {
		UserAccess access = new UserAccess(NOW.minusSeconds(60), NOW.plusSeconds(60), UserAccessStatus.ACTIVE);

		assertThat(access.isActiveAt(NOW)).isTrue();
		assertThat(access.effectiveStatusAt(NOW)).isEqualTo(UserAccessStatus.ACTIVE);
	}

	@Test
	void shouldReportExpiredStatusAtExactExpirationInstant() {
		UserAccess access = new UserAccess(NOW.minusSeconds(120), NOW, UserAccessStatus.ACTIVE);

		assertThat(access.isActiveAt(NOW)).isFalse();
		assertThat(access.effectiveStatusAt(NOW)).isEqualTo(UserAccessStatus.EXPIRED);
	}

	@Test
	void shouldReportPendingStatusBeforeStart() {
		UserAccess access = new UserAccess(NOW.plusSeconds(60), null, UserAccessStatus.ACTIVE);

		assertThat(access.effectiveStatusAt(NOW)).isEqualTo(UserAccessStatus.PENDING);
	}

	@Test
	void shouldRejectSuspendedAccess() {
		UserAccess access = new UserAccess(NOW.minusSeconds(60), null, UserAccessStatus.SUSPENDED);

		assertThat(access.isActiveAt(NOW)).isFalse();
	}

	@Test
	void shouldCapSessionAtAccessExpiration() {
		UserAccess access = new UserAccess(NOW.minusSeconds(60), NOW.plusSeconds(300), UserAccessStatus.ACTIVE);

		assertThat(access.capSessionExpiration(NOW.plusSeconds(3600))).isEqualTo(NOW.plusSeconds(300));
	}
}
