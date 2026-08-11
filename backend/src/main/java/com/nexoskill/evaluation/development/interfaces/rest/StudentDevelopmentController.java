package com.nexoskill.evaluation.development.interfaces.rest;

import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationAnswerCommand;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.PracticeAnswerCommand;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.StartPracticeCommand;
import com.nexoskill.evaluation.development.application.service.StudentEvaluationService;
import com.nexoskill.evaluation.development.application.service.StudentPracticeService;
import com.nexoskill.evaluation.development.application.service.StudentPathService;
import com.nexoskill.evaluation.development.application.service.StudentDevelopmentService;
import com.nexoskill.evaluation.organizations.application.OrganizationBrandingService;
import com.nexoskill.evaluation.organizations.application.OrganizationBrandingService.BrandView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/development")
@PreAuthorize("hasAuthority('STUDENT_PORTAL')")
public class StudentDevelopmentController {
    private final StudentPracticeService practices;
    private final StudentEvaluationService evaluations;
    private final StudentPathService paths;
    private final StudentDevelopmentService development;
    private final OrganizationBrandingService branding;

    public StudentDevelopmentController(StudentPracticeService practices, StudentEvaluationService evaluations,
            StudentPathService paths, StudentDevelopmentService development, OrganizationBrandingService branding) {
        this.practices = practices;
        this.evaluations = evaluations;
        this.paths = paths;
        this.development = development;
        this.branding = branding;
    }

    @GetMapping("/home")
    public Object home(@AuthenticationPrincipal AuthenticatedStudent student) { return development.home(student); }

    @GetMapping("/certifications")
    public Object certifications(@AuthenticationPrincipal AuthenticatedStudent student) { return development.certifications(student); }

    @GetMapping("/branding")
    public Object branding(@AuthenticationPrincipal AuthenticatedStudent student) { return branding.studentBrand(student.organizationId()); }

    @GetMapping("/config")
    public Object config(@AuthenticationPrincipal AuthenticatedStudent student) {
        return new StudentExperienceConfig(branding.studentBrand(student.organizationId()),
                development.certificationModulesEnabled(student));
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<byte[]> brandingLogo(@AuthenticationPrincipal AuthenticatedStudent student) {
        var logo = branding.studentLogo(student.organizationId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(logo.content());
    }

    @GetMapping("/study/options")
    public Object studyOptions(@AuthenticationPrincipal AuthenticatedStudent student) { return practices.options(student); }

    @PostMapping("/practices")
    public Object startPractice(@AuthenticationPrincipal AuthenticatedStudent student, @RequestBody StartPracticeCommand command) {
        return practices.start(student, command);
    }

    @GetMapping("/practices/{publicId}")
    public Object practice(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String publicId) {
        return practices.get(student, publicId);
    }

    @PostMapping("/practices/{publicId}/questions/{questionPublicId}/answer")
    public Object answerPractice(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String publicId,
            @PathVariable String questionPublicId, @RequestBody PracticeAnswerCommand command) {
        return practices.answer(student, publicId, questionPublicId, command);
    }

    @GetMapping("/practices/{publicId}/result")
    public Object practiceResult(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String publicId) {
        return practices.result(student, publicId);
    }

    @PostMapping("/practices/{publicId}/abandon")
    public void abandonPractice(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String publicId) {
        practices.abandon(student, publicId);
    }

    @GetMapping("/paths")
    public Object paths(@AuthenticationPrincipal AuthenticatedStudent student) { return paths.mine(student); }

    @GetMapping("/paths/{assignmentPublicId}")
    public Object path(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String assignmentPublicId) {
        return paths.mine(student, assignmentPublicId);
    }

    @GetMapping("/evaluations")
    public Object evaluations(@AuthenticationPrincipal AuthenticatedStudent student) { return evaluations.list(student); }

    @PostMapping("/evaluations/{assignmentPublicId}/start")
    public Object startEvaluation(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String assignmentPublicId) {
        return evaluations.start(student, assignmentPublicId);
    }

    @GetMapping("/evaluations/attempts/{attemptPublicId}")
    public Object evaluationAttempt(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String attemptPublicId) {
        return evaluations.attempt(student, attemptPublicId);
    }

    @PostMapping("/evaluations/attempts/{attemptPublicId}/questions/{questionPublicId}/answer")
    public Object answerEvaluation(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String attemptPublicId,
            @PathVariable String questionPublicId, @RequestBody EvaluationAnswerCommand command) {
        return evaluations.answer(student, attemptPublicId, questionPublicId, command);
    }

    @PostMapping("/evaluations/attempts/{attemptPublicId}/submit")
    public Object submitEvaluation(@AuthenticationPrincipal AuthenticatedStudent student, @PathVariable String attemptPublicId) {
        return evaluations.submit(student, attemptPublicId);
    }
    public record StudentExperienceConfig(BrandView branding, boolean certificationsEnabled) {}

}
