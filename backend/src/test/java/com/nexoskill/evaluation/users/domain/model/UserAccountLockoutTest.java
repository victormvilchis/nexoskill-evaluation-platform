package com.nexoskill.evaluation.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAccountLockoutTest {

	@Test
	void doesNotExtendAnActiveLockAndRestartsCounterAfterItExpires() {
		Instant base = Instant.parse("2026-07-24T12:00:00Z");
		UserAccount user = user();

		user.registerFailedLogin(2, base, Duration.ofMinutes(15));
		user.registerFailedLogin(2, base.plusSeconds(1), Duration.ofMinutes(15));
		Instant originalLock = user.getLockedUntil();

		user.registerFailedLogin(2, base.plusSeconds(30), Duration.ofMinutes(15));
		assertThat(user.getLockedUntil()).isEqualTo(originalLock);
		assertThat(user.getFailedLoginAttempts()).isEqualTo(2);

		user.registerFailedLogin(2, originalLock.plusSeconds(1), Duration.ofMinutes(15));
		assertThat(user.getLockedUntil()).isNull();
		assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
	}

	private UserAccount user() {
		return new UserAccount(1L, "7a6ad962-20a8-4303-b98e-770997f48db8", "user@nexoskill.local",
				"USER@NEXOSKILL.LOCAL", "hash", "User", "Test", "User Test", UserStatus.ACTIVE, 0, null, null, false,
				null, null, Set.of(new RoleGrant("USER", Set.of())),
				new UserAccess(Instant.parse("2026-01-01T00:00:00Z"), null, UserAccessStatus.ACTIVE));
	}
}
