package com.nexoskill.evaluation.users.application.port.out;

import java.time.Instant;
import java.util.List;

public interface PasswordHistoryPort {

    List<String> recentHashes(Long userId, int limit);

    void record(Long userId, String passwordHash, Instant createdAt);
}
