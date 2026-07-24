package com.nexoskill.evaluation.authentication.application.port.out;

import java.time.Instant;

public interface LoginAttemptPort {

    void record(
            Long userId,
            String attemptedEmail,
            boolean successful,
            String failureReason,
            String ipAddress,
            String userAgent,
            Instant attemptedAt
    );
}
