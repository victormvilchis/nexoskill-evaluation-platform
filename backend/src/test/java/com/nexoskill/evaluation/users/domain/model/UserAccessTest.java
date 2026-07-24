package com.nexoskill.evaluation.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccessTest {

    private static final Instant NOW = Instant.parse("2026-07-24T18:00:00Z");

    @Test
    void shouldAllowAccessInsideActiveWindow() {
        UserAccess access = new UserAccess(
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                UserAccessStatus.ACTIVE
        );

        assertThat(access.isActiveAt(NOW)).isTrue();
    }

    @Test
    void shouldRejectExpiredAccess() {
        UserAccess access = new UserAccess(
                NOW.minusSeconds(120),
                NOW.minusSeconds(60),
                UserAccessStatus.ACTIVE
        );

        assertThat(access.isActiveAt(NOW)).isFalse();
    }

    @Test
    void shouldRejectSuspendedAccess() {
        UserAccess access = new UserAccess(
                NOW.minusSeconds(60),
                null,
                UserAccessStatus.SUSPENDED
        );

        assertThat(access.isActiveAt(NOW)).isFalse();
    }
}
