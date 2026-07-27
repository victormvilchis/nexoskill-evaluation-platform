package com.nexoskill.evaluation.certifications.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.application.CertificationModels.*;
import com.nexoskill.evaluation.certifications.domain.*;
import com.nexoskill.evaluation.certifications.infrastructure.persistence.*;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentCertificationService {
    private static final String ACTIVE = "ACTIVE";

    private final StudentRepository studentRepository;
    private final OrganizationRepository organizationRepository;
    private final ProfessionalCertificationProfileRepository profileCatalogRepository;
    private final CertificationTechnologyRepository technologyRepository;
    private final OrganizationCertificationPolicyRepository policyRepository;
    private final StudentCertificationProfileRepository certificationProfileRepository;
    private final StudentCertificationRequirementRepository requirementRepository;
    private final StudentCertificationAttemptRepository attemptRepository;
    private final StudentCertificationHistoryRepository historyRepository;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public StudentCertificationService(StudentRepository studentRepository,
            OrganizationRepository organizationRepository,
            ProfessionalCertificationProfileRepository profileCatalogRepository,
            CertificationTechnologyRepository technologyRepository,
            OrganizationCertificationPolicyRepository policyRepository,
            StudentCertificationProfileRepository certificationProfileRepository,
            StudentCertificationRequirementRepository requirementRepository,
            StudentCertificationAttemptRepository attemptRepository,
            StudentCertificationHistoryRepository historyRepository,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.studentRepository = studentRepository;
        this.organizationRepository = organizationRepository;
        this.profileCatalogRepository = profileCatalogRepository;
        this.technologyRepository = technologyRepository;
        this.policyRepository = policyRepository;
        this.certificationProfileRepository = certificationProfileRepository;
        this.requirementRepository = requirementRepository;
        this.attemptRepository = attemptRepository;
        this.historyRepository = historyRepository;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Availability availability(TenantContext tenant, AuthenticatedUser actor) {
        OrganizationJpaEntity organization = requireOrganization(tenant);
        boolean operator = hasOperationalRole(actor);
        return new Availability(
                organization.getOrganizationType() == OrganizationType.CUSTOMER
                        && organization.isAppliesCertifications(),
                operator,
                organization.getPublicId(),
                organization.getName());
    }

    @Transactional(readOnly = true)
    public Catalogs catalogs(TenantContext tenant, AuthenticatedUser actor) {
        requireManageableOrganization(tenant, actor);
        List<CatalogItem> profiles = profileCatalogRepository.findAllByStatusOrderBySortOrderAscNameAsc(ACTIVE)
                .stream()
                .map(item -> new CatalogItem(item.getPublicId(), item.getCode(), item.getName(),
                        item.getSuggestedTechnologicalProfile() == null
                                ? null : item.getSuggestedTechnologicalProfile().name()))
                .toList();
        List<CatalogItem> technologies = technologyRepository.findAllByStatusOrderBySortOrderAscNameAsc(ACTIVE)
                .stream()
                .map(item -> new CatalogItem(item.getPublicId(), item.getCode(), item.getName(), null))
                .toList();

        return new Catalogs(profiles, technologies,
                enumOptions(TechnologicalProfile.values(), this::technologicalProfileLabel),
                enumOptions(CertificationStatus.values(), this::certificationStatusLabel),
                enumOptions(CertificationExamStatus.values(), this::examStatusLabel),
                enumOptions(CertificationType.values(), this::certificationTypeLabel));
    }

    @Transactional
    public StudentCertificationDetail get(TenantContext tenant, String studentPublicId, AuthenticatedUser actor) {
        OrganizationJpaEntity organization = requireManageableOrganization(tenant, actor);
        StudentJpaEntity student = requireStudent(organization.getId(), studentPublicId);
        return detail(organization, student);
    }

    @Transactional
    public StudentCertificationDetail save(TenantContext tenant, String studentPublicId,
            SaveCommand command, AuthenticatedUser actor, String ipAddress, String userAgent) {
        OrganizationJpaEntity organization = requireManageableOrganization(tenant, actor);
        StudentJpaEntity student = requireStudent(organization.getId(), studentPublicId);
        validateCommand(command);

        ProfessionalCertificationProfileJpaEntity professionalProfile =
                profileCatalogRepository.findByPublicIdAndStatus(command.professionalProfilePublicId(), ACTIVE)
                        .orElseThrow(() -> new BusinessException("CERTIFICATION_PROFILE_INVALID",
                                "Selecciona un perfil de certificación activo."));
        CertificationTechnologyJpaEntity technology =
                technologyRepository.findByPublicIdAndStatus(command.certificationTechnologyPublicId(), ACTIVE)
                        .orElseThrow(() -> new BusinessException("CERTIFICATION_TECHNOLOGY_INVALID",
                                "Selecciona una tecnología de certificación activa."));

        Instant now = clock.instant();
        StudentCertificationProfileJpaEntity profile =
                certificationProfileRepository.findByStudentIdAndOrganizationId(student.getId(), organization.getId())
                        .orElse(null);
        String previousProfile = profile == null ? null : profileSnapshot(profile);
        boolean created = profile == null;
        if (created) {
            profile = StudentCertificationProfileJpaEntity.create(student.getId(), organization.getId(),
                    professionalProfile.getId(), technology.getId(), command.enrollmentDate(),
                    command.technologicalProfile(), actor.internalId(), now);
            profile = certificationProfileRepository.saveAndFlush(profile);
        } else {
            if (command.profileVersion() == null || !Objects.equals(profile.getVersion(), command.profileVersion())) {
                throw new BusinessException("CERTIFICATION_PROFILE_VERSION_CONFLICT",
                        "La información de certificaciones fue modificada por otra sesión. Actualiza la página.");
            }
            profile.update(professionalProfile.getId(), technology.getId(), command.enrollmentDate(),
                    command.technologicalProfile(), actor.internalId(), now);
        }

        Map<CertificationType, OrganizationCertificationPolicyJpaEntity> policies =
                ensurePolicies(organization.getId(), actor.internalId(), now);
        Map<CertificationType, StudentCertificationRequirementJpaEntity> currentRequirements =
                requirementRepository.findAllByProfileIdOrderByCertificationTypeAsc(profile.getId())
                        .stream().collect(Collectors.toMap(
                                StudentCertificationRequirementJpaEntity::getCertificationType,
                                Function.identity()));

        Map<CertificationType, RequirementCommand> requested = command.requirements() == null
                ? Map.of()
                : command.requirements().stream().collect(Collectors.toMap(
                        RequirementCommand::type, Function.identity(),
                        (left, right) -> {
                            throw new BusinessException("CERTIFICATION_REQUIREMENT_DUPLICATED",
                                    "No se puede enviar dos veces el mismo tipo de certificación.");
                        }));

        for (CertificationType type : CertificationType.values()) {
            StudentCertificationRequirementJpaEntity requirement = currentRequirements.get(type);
            LocalDate calculatedDeadline = calculateDeadline(command.enrollmentDate(), policies.get(type));
            if (requirement == null) {
                requirement = StudentCertificationRequirementJpaEntity.create(profile.getId(), organization.getId(),
                        type, calculatedDeadline, actor.internalId(), now);
                requirement = requirementRepository.saveAndFlush(requirement);
                currentRequirements.put(type, requirement);
            }
            RequirementCommand item = requested.get(type);
            if (item == null) {
                continue;
            }
            if (item.version() != null && !Objects.equals(item.version(), requirement.getVersion())) {
                throw new BusinessException("CERTIFICATION_REQUIREMENT_VERSION_CONFLICT",
                        "El seguimiento de " + certificationTypeLabel(type)
                                + " fue modificado por otra sesión.");
            }
            validateRequirementDates(command.enrollmentDate(), item);
            String previous = requirementSnapshot(requirement);
            requirement.update(item.applies(),
                    item.certificationStatus() == null ? CertificationStatus.PENDING : item.certificationStatus(),
                    item.examStatus() == null ? CertificationExamStatus.NOT_SCHEDULED : item.examStatus(),
                    calculatedDeadline, item.manualDeadline(), item.deadlineOverrideReason(),
                    item.applicationDate(), item.score(), item.currentAttempt(),
                    item.actionsToTake(), item.observations(), actor.internalId(), now);
            historyRepository.save(StudentCertificationHistoryJpaEntity.create(
                    student.getId(), organization.getId(), requirement.getId(),
                    "CERTIFICATION_REQUIREMENT_UPDATED", previous, requirementSnapshot(requirement),
                    item.deadlineOverrideReason(), actor.internalId(), now));
        }

        if (command.newAttempts() != null) {
            for (AttemptCommand attempt : command.newAttempts()) {
                registerAttemptInternal(student, organization, currentRequirements, attempt, actor, now);
            }
        }

        historyRepository.save(StudentCertificationHistoryJpaEntity.create(
                student.getId(), organization.getId(), null,
                created ? "CERTIFICATION_PROFILE_CREATED" : "CERTIFICATION_PROFILE_UPDATED",
                previousProfile, profileSnapshot(profile), null, actor.internalId(), now));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("studentPublicId", student.getPublicId());
        eventData.put("organizationPublicId", organization.getPublicId());
        eventData.put("profilePublicId", profile.getPublicId());
        auditLogPort.record(actor.internalId(),
                created ? "STUDENT_CERTIFICATION_PROFILE_CREATED" : "STUDENT_CERTIFICATION_UPDATED",
                "STUDENT_CERTIFICATIONS",
                created ? "Se creó el perfil de certificación del estudiante."
                        : "Se actualizó el seguimiento de certificaciones del estudiante.",
                ipAddress, userAgent, eventData, now);

        requirementRepository.flush();
        certificationProfileRepository.flush();
        return detail(organization, student);
    }

    private void registerAttemptInternal(StudentJpaEntity student, OrganizationJpaEntity organization,
            Map<CertificationType, StudentCertificationRequirementJpaEntity> requirements,
            AttemptCommand command, AuthenticatedUser actor, Instant now) {
        if (command == null || command.type() == null) {
            throw new BusinessException("CERTIFICATION_ATTEMPT_TYPE_REQUIRED",
                    "Selecciona el tipo de certificación del intento.");
        }
        StudentCertificationRequirementJpaEntity requirement = requirements.get(command.type());
        if (requirement == null) {
            throw new BusinessException("CERTIFICATION_REQUIREMENT_NOT_FOUND",
                    "Primero guarda el requisito de certificación.");
        }
        if (attemptRepository.existsByRequirementIdAndAttemptNumber(requirement.getId(), command.attemptNumber())) {
            throw new BusinessException("CERTIFICATION_ATTEMPT_DUPLICATED",
                    "Ya existe el intento " + command.attemptNumber() + " para "
                            + certificationTypeLabel(command.type()) + ".");
        }
        if (command.applicationDate() != null
                && command.applicationDate().isBefore(
                        certificationProfileRepository.findByStudentIdAndOrganizationId(
                                student.getId(), organization.getId()).orElseThrow().getEnrollmentDate())) {
            throw new BusinessException("CERTIFICATION_APPLICATION_DATE_INVALID",
                    "La fecha de aplicación no puede ser anterior a la fecha de alta.");
        }
        StudentCertificationAttemptJpaEntity attempt = StudentCertificationAttemptJpaEntity.create(
                requirement.getId(), organization.getId(), command.attemptNumber(),
                command.scheduledDate(), command.applicationDate(),
                command.examStatus() == null ? CertificationExamStatus.NOT_SCHEDULED : command.examStatus(),
                command.score(), command.result(), command.observations(), actor.internalId(), now);
        attemptRepository.save(attempt);
        historyRepository.save(StudentCertificationHistoryJpaEntity.create(
                student.getId(), organization.getId(), requirement.getId(),
                "CERTIFICATION_ATTEMPT_REGISTERED", null,
                "attempt=" + command.attemptNumber() + ";status=" + attempt.getExamStatus(),
                null, actor.internalId(), now));
    }

    private StudentCertificationDetail detail(OrganizationJpaEntity organization, StudentJpaEntity student) {
        StudentCertificationProfileJpaEntity profile =
                certificationProfileRepository.findByStudentIdAndOrganizationId(student.getId(), organization.getId())
                        .orElse(null);
        if (profile == null) {
            return new StudentCertificationDetail(student.getPublicId(), student.getDisplayName(),
                    organization.getPublicId(), organization.getName(), true, null,
                    defaultRequirements(organization.getId()), List.of(), history(student));
        }

        ProfessionalCertificationProfileJpaEntity professional =
                profileCatalogRepository.findById(profile.getProfessionalProfileId()).orElseThrow();
        CertificationTechnologyJpaEntity technology =
                technologyRepository.findById(profile.getCertificationTechnologyId()).orElseThrow();

        List<StudentCertificationRequirementJpaEntity> requirements =
                requirementRepository.findAllByProfileIdOrderByCertificationTypeAsc(profile.getId());
        List<RequirementView> requirementViews = requirements.stream().map(this::requirementView).toList();
        Map<Long, CertificationType> typesByRequirement = requirements.stream()
                .collect(Collectors.toMap(StudentCertificationRequirementJpaEntity::getId,
                        StudentCertificationRequirementJpaEntity::getCertificationType));
        List<AttemptView> attempts = requirements.stream()
                .flatMap(requirement -> attemptRepository.findAllByRequirementIdOrderByAttemptNumberAsc(
                        requirement.getId()).stream())
                .map(attempt -> new AttemptView(attempt.getPublicId(),
                        typesByRequirement.get(attempt.getRequirementId()), attempt.getAttemptNumber(),
                        attempt.getScheduledDate(), attempt.getApplicationDate(), attempt.getExamStatus(),
                        attempt.getScore(), attempt.getResult(), attempt.getObservations(), attempt.getCreatedAt()))
                .sorted(Comparator.comparing(AttemptView::type).thenComparingInt(AttemptView::attemptNumber))
                .toList();

        ProfileView profileView = new ProfileView(profile.getPublicId(), professional.getPublicId(),
                professional.getName(), technology.getPublicId(), technology.getName(),
                profile.getEnrollmentDate(), profile.getTechnologicalProfile(), profile.getVersion());
        return new StudentCertificationDetail(student.getPublicId(), student.getDisplayName(),
                organization.getPublicId(), organization.getName(), true, profileView,
                requirementViews, attempts, history(student));
    }

    private List<RequirementView> defaultRequirements(Long organizationId) {
        // No se persisten requisitos vacíos. La vista recibe únicamente una plantilla operativa.
        return Arrays.stream(CertificationType.values())
                .map(type -> new RequirementView(null, type, false,
                        CertificationStatus.NOT_APPLICABLE, CertificationExamStatus.NOT_SCHEDULED,
                        null, null, null, null, null, null, null, null, null, null))
                .toList();
    }

    private List<HistoryView> history(StudentJpaEntity student) {
        return historyRepository.findTop100ByStudentIdAndOrganizationIdOrderByChangedAtDesc(
                        student.getId(), student.getOrganizationId())
                .stream()
                .map(item -> new HistoryView(item.getPublicId(), item.getEventType(),
                        item.getPreviousValues(), item.getNewValues(), item.getReason(), item.getChangedAt()))
                .toList();
    }

    private RequirementView requirementView(StudentCertificationRequirementJpaEntity item) {
        return new RequirementView(item.getPublicId(), item.getCertificationType(), item.isApplies(),
                item.getCertificationStatus(), item.getExamStatus(), item.getCalculatedDeadline(),
                item.getManualDeadline(), item.effectiveDeadline(), item.getDeadlineOverrideReason(),
                item.getApplicationDate(), item.getScore(), item.getCurrentAttempt(),
                item.getActionsToTake(), item.getObservations(), item.getVersion());
    }

    private Map<CertificationType, OrganizationCertificationPolicyJpaEntity> ensurePolicies(
            Long organizationId, Long actorId, Instant now) {
        Map<CertificationType, OrganizationCertificationPolicyJpaEntity> policies =
                policyRepository.findAllByOrganizationIdAndStatus(organizationId, ACTIVE).stream()
                        .collect(Collectors.toMap(OrganizationCertificationPolicyJpaEntity::getCertificationType,
                                Function.identity()));
        for (CertificationType type : CertificationType.values()) {
            if (!policies.containsKey(type)) {
                Integer months = type == CertificationType.DEVELOPMENT_SECURITY
                        || type == CertificationType.NORMATIVE_TESTING ? 2 : null;
                Integer days = type == CertificationType.DEVELOPMENT_SECURITY ? 15
                        : type == CertificationType.NORMATIVE_TESTING ? 0 : null;
                OrganizationCertificationPolicyJpaEntity created =
                        policyRepository.save(OrganizationCertificationPolicyJpaEntity.create(
                                organizationId, type, months, days, actorId, now));
                policies.put(type, created);
            }
        }
        return policies;
    }

    private LocalDate calculateDeadline(LocalDate enrollment,
            OrganizationCertificationPolicyJpaEntity policy) {
        return policy == null ? null : CertificationDeadlineCalculator.calculate(
                enrollment, policy.getDeadlineMonths(), policy.getDeadlineDays());
    }

    private OrganizationJpaEntity requireManageableOrganization(TenantContext tenant, AuthenticatedUser actor) {
        if (!hasOperationalRole(actor)) {
            throw new BusinessException("CERTIFICATION_ACCESS_FORBIDDEN",
                    "Solo Gestores y Supervisores pueden administrar certificaciones.");
        }
        OrganizationJpaEntity organization = requireOrganization(tenant);
        if (organization.getOrganizationType() != OrganizationType.CUSTOMER) {
            throw new BusinessException("CERTIFICATION_GLOBAL_NOT_SUPPORTED",
                    "La organización global no utiliza gestión operativa de certificaciones.");
        }
        if (!organization.isAppliesCertifications()) {
            throw new BusinessException("CERTIFICATION_FEATURE_DISABLED",
                    "La organización no tiene habilitada la gestión de certificaciones.");
        }
        return organization;
    }

    private OrganizationJpaEntity requireOrganization(TenantContext tenant) {
        if (tenant == null || tenant.organizationId() == null || tenant.globalScope()) {
            throw new BusinessException("CERTIFICATION_ORGANIZATION_REQUIRED",
                    "No fue posible determinar una organización comercial para la operación.");
        }
        return organizationRepository.findById(tenant.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND",
                        "La organización asociada no existe."));
    }

    private StudentJpaEntity requireStudent(Long organizationId, String publicId) {
        StudentJpaEntity student = studentRepository.findByOrganizationIdAndPublicId(organizationId, publicId)
                .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND",
                        "El estudiante solicitado no existe dentro de la organización."));
        if (student.getStatus() == StudentStatus.DELETED) {
            throw new BusinessException("STUDENT_DELETED",
                    "No se pueden administrar certificaciones de un estudiante eliminado.");
        }
        return student;
    }

    private boolean hasOperationalRole(AuthenticatedUser actor) {
        return actor != null && actor.roles().stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.equals("MANAGER") || value.equals("SUPERVISOR"));
    }

    private void validateCommand(SaveCommand command) {
        if (command == null || command.professionalProfilePublicId() == null
                || command.professionalProfilePublicId().isBlank()) {
            throw new BusinessException("CERTIFICATION_PROFILE_REQUIRED",
                    "Selecciona el perfil de certificación.");
        }
        if (command.certificationTechnologyPublicId() == null
                || command.certificationTechnologyPublicId().isBlank()) {
            throw new BusinessException("CERTIFICATION_TECHNOLOGY_REQUIRED",
                    "Selecciona la tecnología de certificación.");
        }
        if (command.enrollmentDate() == null) {
            throw new BusinessException("CERTIFICATION_ENROLLMENT_DATE_REQUIRED",
                    "La fecha de alta es obligatoria.");
        }
        if (command.technologicalProfile() == null) {
            throw new BusinessException("CERTIFICATION_TECH_PROFILE_REQUIRED",
                    "Selecciona el perfil tecnológico.");
        }
    }

    private void validateRequirementDates(LocalDate enrollmentDate, RequirementCommand item) {
        if (item.applicationDate() != null && item.applicationDate().isBefore(enrollmentDate)) {
            throw new BusinessException("CERTIFICATION_APPLICATION_DATE_INVALID",
                    "La fecha de aplicación no puede ser anterior a la fecha de alta.");
        }
        if (item.actionsToTake() != null && item.actionsToTake().length() > 1000) {
            throw new BusinessException("CERTIFICATION_ACTIONS_TOO_LONG",
                    "Las acciones a realizar no pueden superar 1000 caracteres.");
        }
        if (item.observations() != null && item.observations().length() > 1000) {
            throw new BusinessException("CERTIFICATION_OBSERVATIONS_TOO_LONG",
                    "Las observaciones no pueden superar 1000 caracteres.");
        }
    }

    private String profileSnapshot(StudentCertificationProfileJpaEntity profile) {
        return "profileId=" + profile.getProfessionalProfileId()
                + ";technologyId=" + profile.getCertificationTechnologyId()
                + ";enrollmentDate=" + profile.getEnrollmentDate()
                + ";technologicalProfile=" + profile.getTechnologicalProfile();
    }

    private String requirementSnapshot(StudentCertificationRequirementJpaEntity requirement) {
        return "type=" + requirement.getCertificationType()
                + ";applies=" + requirement.isApplies()
                + ";certificationStatus=" + requirement.getCertificationStatus()
                + ";examStatus=" + requirement.getExamStatus()
                + ";calculatedDeadline=" + requirement.getCalculatedDeadline()
                + ";manualDeadline=" + requirement.getManualDeadline()
                + ";applicationDate=" + requirement.getApplicationDate()
                + ";score=" + requirement.getScore()
                + ";attempt=" + requirement.getCurrentAttempt();
    }

    private <E extends Enum<E>> List<EnumOption> enumOptions(E[] values, Function<E, String> label) {
        return Arrays.stream(values).map(value -> new EnumOption(value.name(), label.apply(value))).toList();
    }

    private String technologicalProfileLabel(TechnologicalProfile value) {
        return switch (value) {
            case DEVELOPER -> "Desarrollador";
            case FUNCTIONAL -> "Funcional";
            case SPECIALIZED_PLATFORM -> "Plataforma especializada";
        };
    }

    private String certificationTypeLabel(CertificationType value) {
        return switch (value) {
            case DEVELOPMENT_SECURITY -> "Desarrollo Seguro";
            case TECHNOLOGICAL -> "Certificación tecnológica";
            case ONE -> "ONE";
            case NORMATIVE_TESTING -> "Normativa y Testing";
            case AGILE -> "Agile";
        };
    }

    private String certificationStatusLabel(CertificationStatus value) {
        return switch (value) {
            case NOT_APPLICABLE -> "No aplica";
            case PENDING -> "Pendiente";
            case IN_PROGRESS -> "En progreso";
            case SCHEDULED -> "Programada";
            case CERTIFIED -> "Certificado";
            case NOT_CERTIFIED -> "No certificado";
            case EXPIRED -> "Vencida";
            case CANCELLED -> "Cancelada";
        };
    }

    private String examStatusLabel(CertificationExamStatus value) {
        return switch (value) {
            case NOT_SCHEDULED -> "Sin programar";
            case SCHEDULED -> "Programado";
            case RESCHEDULED -> "Reprogramado";
            case COMPLETED -> "Completado";
            case PASSED -> "Aprobado";
            case FAILED -> "No aprobado";
            case ABSENT -> "Ausente";
            case CANCELLED -> "Cancelado";
        };
    }
}
