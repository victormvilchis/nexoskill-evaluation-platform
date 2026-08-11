package com.nexoskill.evaluation.development.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.AssignEvaluationCommand;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.AssignPathCommand;
import com.nexoskill.evaluation.development.application.service.StudentEvaluationService;
import com.nexoskill.evaluation.development.application.service.StudentPathService;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/students/{studentPublicId}/development")
public class AdminStudentDevelopmentController {
    private final StudentEvaluationService evaluations;
    private final StudentPathService paths;
    private final TenantContextResolver tenants;

    public AdminStudentDevelopmentController(StudentEvaluationService evaluations, StudentPathService paths,
            TenantContextResolver tenants) {
        this.evaluations = evaluations;
        this.paths = paths;
        this.tenants = tenants;
    }

    @GetMapping("/evaluation-options")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and hasAuthority('FORM_VIEW')")
    public Object evaluationOptions(@PathVariable String studentPublicId, HttpServletRequest request) {
        return evaluations.assignableForms(tenants.resolve(request), studentPublicId);
    }

    @PostMapping("/evaluations")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and hasAuthority('FORM_VIEW')")
    public Object assignEvaluation(@PathVariable String studentPublicId, @RequestBody AssignEvaluationCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return evaluations.assign(tenants.resolve(request), actor, studentPublicId, command);
    }


    @GetMapping("/path-options")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and hasAuthority('PATH_VIEW')")
    public Object pathOptions(@PathVariable String studentPublicId, HttpServletRequest request) {
        return paths.assignablePaths(tenants.resolve(request), studentPublicId);
    }

    @GetMapping("/paths")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and hasAuthority('PATH_VIEW')")
    public Object assignedPaths(@PathVariable String studentPublicId, HttpServletRequest request) {
        return paths.assignedPaths(tenants.resolve(request), studentPublicId);
    }

    @PostMapping("/paths")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and hasAuthority('PATH_MANAGE')")
    public Object assignPath(@PathVariable String studentPublicId, @RequestBody AssignPathCommand command,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return paths.assign(tenants.resolve(request), actor, studentPublicId, command);
    }

    @DeleteMapping("/paths/{assignmentPublicId}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') and hasAuthority('PATH_MANAGE')")
    public void removePath(@PathVariable String studentPublicId, @PathVariable String assignmentPublicId,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        paths.remove(tenants.resolve(request), actor, studentPublicId, assignmentPublicId);
    }
}
