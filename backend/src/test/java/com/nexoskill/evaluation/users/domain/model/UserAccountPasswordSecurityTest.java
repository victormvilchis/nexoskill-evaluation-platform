package com.nexoskill.evaluation.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAccountPasswordSecurityTest {

    private static final Instant NOW = Instant.parse("2026-07-24T21:00:00Z");

    @Test
    void shouldReportTemporaryPasswordExpiredAtExactExpiration() {
        UserAccount user = user(true, NOW);

        assertThat(user.isTemporaryPasswordExpiredAt(NOW)).isTrue();
    }

    @Test
    void shouldCapSessionAtTemporaryPasswordExpiration() {
        UserAccount user = user(true, NOW.plusSeconds(120));

        assertThat(user.capSessionExpirationForPassword(NOW.plusSeconds(3600)))
                .isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void shouldClearTemporaryPasswordStateAfterPasswordChange() {
        UserAccount user = user(true, NOW.plusSeconds(120));

        user.changePassword("new-hash", NOW);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isPasswordChangeRequired()).isFalse();
        assertThat(user.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
    }

    private UserAccount user(
            boolean passwordChangeRequired,
            Instant temporaryPasswordExpiresAt) {
        return new UserAccount(
                1L,
                "public-id",
                "usuario@nexoskill.local",
                "USUARIO@NEXOSKILL.LOCAL",
                "old-hash",
                "Usuario",
                "NexoSkill",
                "Usuario NexoSkill",
                UserStatus.ACTIVE,
                0,
                null,
                null,
                passwordChangeRequired,
                null,
                temporaryPasswordExpiresAt,
                Set.of(new RoleGrant("USER", Set.of("PASSWORD_CHANGE"))),
                new UserAccess(
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(7200),
                        UserAccessStatus.ACTIVE
                )
        );
    }
}
