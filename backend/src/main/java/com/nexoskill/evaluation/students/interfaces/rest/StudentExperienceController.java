package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.students.application.StudentExperienceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/students/{studentPublicId}/experience")
public class StudentExperienceController {
    private final StudentExperienceService service;
    private final TenantContextResolver tenantResolver;

    public StudentExperienceController(StudentExperienceService service, TenantContextResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentExperienceService.ExperienceView get(@PathVariable String studentPublicId,
            HttpServletRequest request) {
        return service.get(tenantResolver.resolve(request), studentPublicId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentExperienceService.ExperienceView update(@PathVariable String studentPublicId,
            @Valid @RequestBody UpdateRequest body, @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request) {
        return service.update(tenantResolver.resolve(request), studentPublicId,
                new StudentExperienceService.UpdateCommand(
                        map(body.currentTechnologies()), map(body.languages()), map(body.knownTechnologies())),
                actor.internalId());
    }

    private List<StudentExperienceService.ExperienceItemCommand> map(List<ItemRequest> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> new StudentExperienceService.ExperienceItemCommand(value.name(), value.level())).toList();
    }

    public record UpdateRequest(
            @Size(max = 100, message = "No puedes registrar más de 100 tecnologías actuales.") List<@Valid ItemRequest> currentTechnologies,
            @Size(max = 100, message = "No puedes registrar más de 100 lenguajes.") List<@Valid ItemRequest> languages,
            @Size(max = 100, message = "No puedes registrar más de 100 tecnologías conocidas.") List<@Valid ItemRequest> knownTechnologies) {}

    public record ItemRequest(
            @Size(max = 200, message = "El nombre no puede superar 200 caracteres.") String name,
            @Size(max = 10, message = "El nivel no es válido.") String level) {}
}
