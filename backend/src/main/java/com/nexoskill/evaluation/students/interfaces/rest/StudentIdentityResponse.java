package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import java.time.Instant;

public record StudentIdentityResponse(String publicId, String organizationPublicId,
        String organizationCode, String organizationName, String studentCode, String email,
        String firstName, String lastName, String displayName, StudentEffectiveStatus status,
        Instant validFrom, Instant expiresAt, Instant lastLoginAt, boolean passwordChangeRequired) {

    public static StudentIdentityResponse from(AuthenticatedStudent student) {
        return new StudentIdentityResponse(student.publicId(), student.organizationPublicId(),
                student.organizationCode(), student.organizationName(), student.studentCode(), student.email(),
                student.firstName(), student.lastName(), student.displayName(), student.status(),
                student.validFrom(), student.expiresAt(), student.lastLoginAt(), student.passwordChangeRequired());
    }
}
