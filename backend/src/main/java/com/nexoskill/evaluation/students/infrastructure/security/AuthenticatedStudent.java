package com.nexoskill.evaluation.students.infrastructure.security;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import java.time.Instant;

public record AuthenticatedStudent(Long internalId, String publicId, Long organizationId,
        String organizationPublicId, String organizationCode, String organizationName,
        String studentCode, String email, String firstName, String lastName, String displayName,
        StudentEffectiveStatus status, Instant validFrom, Instant expiresAt, Instant lastLoginAt,
        boolean passwordChangeRequired) {
}
