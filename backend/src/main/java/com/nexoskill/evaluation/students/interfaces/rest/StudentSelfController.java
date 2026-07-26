package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
public class StudentSelfController {
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('STUDENT_PORTAL')")
    public StudentResponse me(@AuthenticationPrincipal AuthenticatedStudent student) {
        return new StudentResponse(StudentIdentityResponse.from(student));
    }

    public record StudentResponse(StudentIdentityResponse student) {}
}
