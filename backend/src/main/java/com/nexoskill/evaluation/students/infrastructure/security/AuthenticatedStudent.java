package com.nexoskill.evaluation.students.infrastructure.security;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import java.time.Instant;
import java.time.LocalDate;

public record AuthenticatedStudent(Long internalId, String publicId, Long organizationId, String organizationPublicId,
        String organizationCode, String organizationName, String studentCode, String email, String firstName,
        String lastName, String displayName, StudentEffectiveStatus status, LocalDate validFrom, LocalDate expiresAt,
        Instant lastLoginAt, boolean passwordChangeRequired) {
}
