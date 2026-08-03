package com.nexoskill.evaluation.students.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.interfaces.rest.ClientRequestInfo;
import com.nexoskill.evaluation.shared.interfaces.rest.PaginationParameters;
import com.nexoskill.evaluation.students.application.StudentFoundationService;
import com.nexoskill.evaluation.students.application.StudentService;
import com.nexoskill.evaluation.students.application.TalentBankService;
import com.nexoskill.evaluation.students.application.TalentCvService;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.domain.TalentType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/talent-bank")
public class TalentBankController {
    private final TalentBankService service;
    private final TalentCvService cv;
    private final TenantContextResolver tenantResolver;

    public TalentBankController(TalentBankService service, TalentCvService cv,
            TenantContextResolver tenantResolver) {
        this.service = service;
        this.cv = cv;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public TalentBankService.PageResult search(HttpServletRequest request,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String organizationPublicId,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) String profileCode,
            @RequestParam(required = false) String technologyPublicId,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationParameters.validate(page, size);
        return service.search(tenant(request), new TalentBankService.SearchCriteria(query, organizationPublicId,
                parseType(type), profileCode, technologyPublicId, sort, direction), page, size);
    }

    @GetMapping("/catalogs")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public TalentBankService.CatalogBundle catalogs(@RequestParam(required = false) String organizationPublicId,
            HttpServletRequest request) {
        return service.catalogs(tenant(request), organizationPublicId);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public TalentBankService.TalentView get(@PathVariable String publicId, HttpServletRequest request) {
        return service.get(tenant(request), publicId);
    }

    @GetMapping("/{publicId}/history")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public TalentBankService.HistoryPage history(@PathVariable String publicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        PaginationParameters.validate(page, size);
        return service.history(tenant(request), publicId, page, size);
    }

    @GetMapping("/{publicId}/foundation")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentFoundationService.StudentView foundation(@PathVariable String publicId, HttpServletRequest request) {
        return service.getFoundation(tenant(request), publicId);
    }

    @PostMapping("/academy")
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public TalentCreateResponse createAcademy(@Valid @RequestBody AcademyRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        TalentBankService.CreateResult result = service.createAcademy(tenant(request),
                new TalentBankService.AcademyCommand(body.organizationPublicId(), body.studentCode(), body.email(),
                        body.firstName(), body.lastName(), body.displayName(), body.validFrom(), body.expiresAt(),
                        body.organizationHiredOn(), body.profileCode(), body.technologyPublicId()), actor(user, request));
        return response(result);
    }

    @PostMapping("/prospects")
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public TalentCreateResponse createProspect(@Valid @RequestBody ProspectRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        TalentBankService.CreateResult result = service.createProspect(tenant(request), body.toFoundationCreate(),
                actor(user, request));
        return response(result);
    }

    @PutMapping("/{publicId}/academy")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public TalentBankService.TalentView updateAcademy(@PathVariable String publicId,
            @Valid @RequestBody AcademyUpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return service.updateAcademy(tenant(request), publicId,
                new TalentBankService.AcademyUpdateCommand(body.studentCode(), body.email(), body.firstName(),
                        body.lastName(), body.displayName(), body.validFrom(), body.expiresAt(),
                        body.organizationHiredOn(), body.profileCode(), body.technologyPublicId(), body.version()),
                actor(user, request));
    }

    @PutMapping("/{publicId}/full")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public TalentBankService.TalentView updateFullTalent(@PathVariable String publicId,
            @Valid @RequestBody ProspectUpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return service.updateFullTalent(tenant(request), publicId, body.toFoundationUpdate(), actor(user, request));
    }

    @PostMapping("/{publicId}/convert")
    @PreAuthorize("hasAuthority('STUDENT_CREATE') and hasAuthority('STUDENT_UPDATE')")
    public StudentFoundationService.StudentView convert(@PathVariable String publicId,
            @Valid @RequestBody ProspectUpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return service.convert(tenant(request), publicId, body.toFoundationUpdate(), actor(user, request));
    }

    @GetMapping("/{publicId}/cv")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public TalentCvService.CvMetadata cvMetadata(@PathVariable String publicId, HttpServletRequest request) {
        return cv.metadata(service.effectiveTenant(tenant(request), publicId), publicId);
    }

    @PostMapping(value = "/{publicId}/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public TalentCvService.CvMetadata uploadCv(@PathVariable String publicId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return cv.upload(service.effectiveTenant(tenant(request), publicId), publicId, file, actor(user, request));
    }

    @GetMapping("/{publicId}/cv/download")
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public ResponseEntity<ByteArrayResource> downloadCv(@PathVariable String publicId, HttpServletRequest request) {
        TalentCvService.CvDownload document = cv.download(service.effectiveTenant(tenant(request), publicId), publicId);
        String encoded = URLEncoder.encode(document.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(document.data().length)
                .body(new ByteArrayResource(document.data()));
    }

    private TalentCreateResponse response(TalentBankService.CreateResult result) {
        return new TalentCreateResponse(result.talent());
    }

    private TenantContext tenant(HttpServletRequest request) { return tenantResolver.resolve(request); }
    private StudentService.Actor actor(AuthenticatedUser user, HttpServletRequest request) {
        return new StudentService.Actor(user.internalId(), ClientRequestInfo.ipAddress(request),
                ClientRequestInfo.userAgent(request));
    }

    private TalentType parseType(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        try {
            return TalentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("TALENT_TYPE_INVALID", "El tipo de talento no es válido.");
        }
    }

    public record AcademyRequest(String organizationPublicId,
            @Size(max = 80) String studentCode,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate expiresAt,
            @NotNull LocalDate organizationHiredOn,
            @NotBlank String profileCode,
            @NotBlank String technologyPublicId) {}

    public record AcademyUpdateRequest(@Size(max = 80) String studentCode,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate expiresAt,
            @NotNull LocalDate organizationHiredOn,
            @NotBlank String profileCode,
            @NotBlank String technologyPublicId,
            @NotNull Long version) {}

    public record ProspectRequest(String organizationPublicId,
            @Size(max = 80) String studentCode,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 100) String firstName,
            @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate expiresAt,
            String professionalProfilePublicId, String technologicalProfilePublicId,
            Boolean appliesTechnologicalCertification, Boolean appliesDevelopmentSecurity,
            Boolean appliesNormativeTesting, Boolean appliesOne, Boolean appliesAgile, Boolean appliesJira) {
        StudentFoundationService.CreateCommand toFoundationCreate() {
            return new StudentFoundationService.CreateCommand(organizationPublicId, email, firstName, lastName,
                    displayName, StudentStatus.INACTIVE, validFrom, expiresAt, null, studentCode, null,
                    professionalProfilePublicId, technologicalProfilePublicId,
                    Boolean.TRUE.equals(appliesTechnologicalCertification), Boolean.TRUE.equals(appliesDevelopmentSecurity),
                    Boolean.TRUE.equals(appliesNormativeTesting), Boolean.TRUE.equals(appliesOne),
                    Boolean.TRUE.equals(appliesAgile), Boolean.TRUE.equals(appliesJira));
        }
    }

    public record ProspectUpdateRequest(@Size(max = 80) String studentCode,
            @Size(max = 100) String corporateUser,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 100) String firstName,
            @Size(max = 150) String lastName,
            @Size(max = 250) String displayName,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate expiresAt,
            LocalDate admissionDate,
            String professionalProfilePublicId, String technologicalProfilePublicId,
            Boolean appliesTechnologicalCertification, Boolean appliesDevelopmentSecurity,
            Boolean appliesNormativeTesting, Boolean appliesOne, Boolean appliesAgile, Boolean appliesJira,
            @NotNull Long version) {
        StudentFoundationService.UpdateCommand toFoundationUpdate() {
            return new StudentFoundationService.UpdateCommand(email, firstName, lastName, displayName, validFrom,
                    expiresAt, admissionDate, studentCode, corporateUser, professionalProfilePublicId,
                    technologicalProfilePublicId, Boolean.TRUE.equals(appliesTechnologicalCertification),
                    Boolean.TRUE.equals(appliesDevelopmentSecurity), Boolean.TRUE.equals(appliesNormativeTesting),
                    Boolean.TRUE.equals(appliesOne), Boolean.TRUE.equals(appliesAgile), Boolean.TRUE.equals(appliesJira), version);
        }
    }

    public record TalentCreateResponse(TalentBankService.TalentView talent) {}
}
