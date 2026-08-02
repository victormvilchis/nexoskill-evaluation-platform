package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import com.nexoskill.evaluation.users.application.service.PasswordPolicy;
import com.nexoskill.evaluation.users.application.service.SecureTemporaryPasswordGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
    private final StudentRepository studentRepository;
    private final StudentSessionRepository sessionRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final SecureTemporaryPasswordGenerator passwordGenerator;
    private final AppProperties properties;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    @Autowired
    public StudentService(StudentRepository studentRepository, StudentSessionRepository sessionRepository,
            OrganizationRepository organizationRepository, PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy, SecureTemporaryPasswordGenerator passwordGenerator,
            AppProperties properties, AuditLogPort auditLogPort, Clock clock) {
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.organizationRepository = organizationRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordGenerator = passwordGenerator;
        this.properties = properties;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    /** Constructor conservado para pruebas/unitarios anteriores. */
    public StudentService(StudentRepository studentRepository, StudentSessionRepository sessionRepository,
            OrganizationRepository organizationRepository, PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy, AppProperties properties, AuditLogPort auditLogPort, Clock clock) {
        this(studentRepository, sessionRepository, organizationRepository, passwordHasher, passwordPolicy,
                new SecureTemporaryPasswordGenerator(), properties, auditLogPort, clock);
    }

    @Transactional(readOnly = true)
    public PageResult search(TenantContext tenant, String query, StudentEffectiveStatus status,
            boolean ignoredIncludeDeleted, int page, int size) {
        Long organizationId = requireOrganization(tenant);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (!studentRepository.existsByOrganizationId(organizationId)) return PageResult.empty(safePage, safeSize);
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        Page<StudentJpaEntity> result = studentRepository.search(organizationId, normalizeQuery(query), status, today, pageable);
        List<StudentSummary> content = result.getContent() == null ? List.of()
                : result.getContent().stream().map(entity -> summary(entity, now)).toList();
        return new PageResult(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudentDetail get(TenantContext tenant, String publicId) {
        return detail(findScoped(tenant, publicId), clock.instant());
    }

    @Transactional
    public CreateResult create(TenantContext tenant, CreateCommand command, Actor actor) {
        OrganizationJpaEntity organization = requireOperationalOrganizationEntity(tenant);
        Long organizationId = organization.getId();
        validateRequired(command);
        validateDates(command.validFrom(), command.expiresAt());
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        StudentStatus initialStatus = command.admissionDate() == null ? StudentStatus.INACTIVE : StudentStatus.ACTIVE;
        if (initialStatus == StudentStatus.ACTIVE && command.validFrom().isAfter(today)) {
            throw fieldError("STUDENT_VALID_FROM_FUTURE", "La vigencia todavía no inicia.",
                    "validFrom", "El inicio de vigencia de un colaborador activo no puede ser futuro.");
        }
        if (initialStatus == StudentStatus.ACTIVE && command.expiresAt().isBefore(today)) {
            throw fieldError("STUDENT_EXPIRED", "No se puede crear un colaborador activo vencido.",
                    "expiresAt", "La fecha de vencimiento debe ser igual o posterior a la fecha actual.");
        }
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        if (studentRepository.existsByOrganizationIdAndNormalizedEmail(organizationId, normalizedEmail)) {
            throw fieldError("STUDENT_EMAIL_EXISTS", "El correo ya está registrado.",
                    "email", "Ya existe un colaborador con este correo dentro de la organización.");
        }
        String corporateUser = cleanOptional(command.corporateUser());
        String normalizedCorporateUser = normalizeCorporateUser(corporateUser);
        validateCorporateUser(null, command.admissionDate(), corporateUser, normalizedCorporateUser);
        String requestedCode = cleanStudentCode(command.studentCode());
        if (organization.isManualStudentCode()) {
            if (requestedCode == null) {
                throw fieldError("STUDENT_CODE_REQUIRED", "El Código a nivel organización es obligatorio.",
                        "studentCode", "Captura el Código a nivel organización.");
            }
            validateStudentCodeAvailable(organizationId, null, requestedCode);
        }
        String temporaryPassword = generateValidTemporaryPassword(command.email());
        String publicId = UUID.randomUUID().toString();
        ResolvedName name = resolveName(command.firstName(), command.lastName(), command.displayName());
        String initialCode = organization.isManualStudentCode() ? requestedCode
                : provisionalStudentCode(organization.getName(), publicId);
        StudentJpaEntity student = StudentJpaEntity.create(publicId, organizationId, initialCode,
                command.email().trim(), normalizedEmail, passwordHasher.encode(temporaryPassword),
                name.firstName(), name.lastName(), name.displayName(), initialStatus,
                command.validFrom(), command.expiresAt(), command.admissionDate(), corporateUser, normalizedCorporateUser,
                now.plus(properties.getSecurity().getTemporaryPasswordDuration()), actor.userId(), now);
        try {
            student = studentRepository.saveAndFlush(student);
            if (!organization.isManualStudentCode()) {
                student.assignStudentCode(generateAvailableStudentCode(organization.getName(), organizationId,
                        student.getId()), actor.userId(), now);
                student = studentRepository.saveAndFlush(student);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("STUDENT_CONFLICT",
                    "El correo, el Código a nivel organización o el Usuario corporativo ya está registrado.",
                    Map.of("student", "Verifica los identificadores del colaborador."));
        }
        audit(actor, "STUDENT_CREATED", student,
                Map.of("initialStatus", initialStatus.name(), "passwordChangeRequired", true,
                        "studentCode", student.getStudentCode()), now);
        audit(actor, "STUDENT_TEMPORARY_PASSWORD_GENERATED", student,
                Map.of("oneTimeDisplay", true, "passwordChangeRequired", true), now);
        return new CreateResult(detail(student, now), temporaryPassword);
    }

    @Transactional
    public StudentDetail update(TenantContext tenant, String publicId, UpdateCommand command, Actor actor) {
        StudentJpaEntity student = findScopedForUpdate(tenant, publicId);
        validateVersion(student, command.version());
        validateUpdateRequired(command);
        validateDates(command.validFrom(), command.expiresAt());
        OrganizationJpaEntity organization = organizationRepository.findById(student.getOrganizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        Long currentStudentId = student.getId();
        studentRepository.findByOrganizationIdAndNormalizedEmail(student.getOrganizationId(), normalizedEmail)
                .filter(existing -> !existing.getId().equals(currentStudentId))
                .ifPresent(existing -> {
                    throw fieldError("STUDENT_EMAIL_EXISTS", "El correo ya está registrado.",
                            "email", "Ya existe un colaborador con este correo dentro de la organización.");
                });
        String requestedCode = organization.isManualStudentCode()
                ? cleanStudentCode(command.studentCode()) : student.getStudentCode();
        if (organization.isManualStudentCode()) {
            if (requestedCode == null) {
                throw fieldError("STUDENT_CODE_REQUIRED", "El Código a nivel organización es obligatorio.",
                        "studentCode", "Captura el Código a nivel organización.");
            }
            validateStudentCodeAvailable(student.getOrganizationId(), student.getId(), requestedCode);
        }
        String requestedCorporateUser = cleanOptional(command.corporateUser());
        boolean preserveExistingCorporateUser = command.admissionDate() == null
                && student.getCorporateUser() != null;
        if (preserveExistingCorporateUser && requestedCorporateUser != null
                && !requestedCorporateUser.equalsIgnoreCase(student.getCorporateUser())) {
            throw fieldError("STUDENT_CORPORATE_USER_REQUIRES_ADMISSION_DATE",
                    "Captura una Fecha de alta para modificar el Usuario corporativo.",
                    "corporateUser", "El Usuario corporativo no puede modificarse mientras el colaborador esté inactivo.");
        }
        String corporateUser = preserveExistingCorporateUser
                ? student.getCorporateUser() : requestedCorporateUser;
        String normalizedCorporateUser = normalizeCorporateUser(corporateUser);
        validateCorporateUser(student.getId(), command.admissionDate(), corporateUser,
                normalizedCorporateUser, preserveExistingCorporateUser);
        Instant now = clock.instant();
        LocalDate previousAdmissionDate = student.getAdmissionDate();
        LocalDate previousValidFrom = student.getValidFrom();
        LocalDate previousExpiresAt = student.getExpiresAt();
        ResolvedName name = resolveName(command.firstName(), command.lastName(), command.displayName());
        student.updateProfile(requestedCode, command.email().trim(), normalizedEmail, name.firstName(), name.lastName(),
                name.displayName(), command.validFrom(), command.expiresAt(), command.admissionDate(),
                corporateUser, normalizedCorporateUser, actor.userId(), now);
        try {
            student = studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("STUDENT_CONFLICT",
                    "El correo, el Código a nivel organización o el Usuario corporativo ya está registrado.",
                    Map.of("student", "Verifica los identificadores del colaborador."));
        }
        StudentEffectiveStatus effectiveStatus = student.effectiveStatusOn(LocalDate.now(clock));
        if (effectiveStatus != StudentEffectiveStatus.ACTIVE || (previousAdmissionDate != null && command.admissionDate() == null)) {
            revokeSessions(student.getId(), revocationReason(effectiveStatus), now);
        }
        audit(actor, "STUDENT_UPDATED", student,
                Map.of("previousValidFrom", String.valueOf(previousValidFrom),
                        "newValidFrom", String.valueOf(command.validFrom()),
                        "previousExpiresAt", String.valueOf(previousExpiresAt),
                        "newExpiresAt", String.valueOf(command.expiresAt()),
                        "previousAdmissionDate", String.valueOf(previousAdmissionDate),
                        "newAdmissionDate", String.valueOf(command.admissionDate())), now);
        return detail(student, now);
    }

    @Transactional
    public StudentDetail activate(TenantContext tenant, String publicId, Actor actor) {
        requireOperationalOrganization(tenant);
        StudentJpaEntity student = findScopedForUpdate(tenant, publicId);
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        if (student.getAdmissionDate() == null) {
            throw fieldError("STUDENT_ADMISSION_DATE_REQUIRED", "La Fecha de alta es necesaria para activar al colaborador.",
                    "admissionDate", "Captura una Fecha de alta desde Editar colaborador.");
        }
        if (student.getValidFrom() == null || student.getValidFrom().isAfter(today)) {
            throw fieldError("STUDENT_VALID_FROM_FUTURE", "La vigencia todavía no inicia.",
                    "validFrom", "El inicio de vigencia debe ser igual o anterior a la fecha actual.");
        }
        if (student.getExpiresAt() == null || student.getExpiresAt().isBefore(today)) {
            throw fieldError("STUDENT_ACCESS_DATES_INVALID", "La vigencia no permite activar al estudiante.",
                    "expiresAt", "Actualiza la fecha de vencimiento desde Editar estudiante antes de activarlo.");
        }
        if (student.getStatus() == StudentStatus.ACTIVE) {
            throw new BusinessException("STUDENT_STATUS_UNCHANGED", "El estudiante ya está activo.");
        }
        if (student.getStatus() != StudentStatus.INACTIVE && student.getStatus() != StudentStatus.EXPIRED) {
            throw new BusinessException("STUDENT_STATUS_TRANSITION_INVALID", "El estado actual no permite activar al estudiante.");
        }
        student.activate(actor.userId(), now);
        studentRepository.save(student);
        audit(actor, "STUDENT_ACTIVATED", student, Map.of(), now);
        return detail(student, now);
    }

    @Transactional
    public StudentDetail deactivate(TenantContext tenant, String publicId, Actor actor) {
        StudentJpaEntity student = findScopedForUpdate(tenant, publicId);
        Instant now = clock.instant();
        if (student.getStatus() == StudentStatus.INACTIVE) {
            throw new BusinessException("STUDENT_STATUS_UNCHANGED", "El estudiante ya está desactivado.");
        }
        if (student.getStatus() == StudentStatus.DELETED) {
            throw new BusinessException("STUDENT_STATUS_TRANSITION_INVALID", "El estudiante ya no está disponible.");
        }
        student.deactivate(actor.userId(), now);
        studentRepository.save(student);
        revokeSessions(student.getId(), StudentSessionRevocationReason.DEACTIVATED, now);
        audit(actor, "STUDENT_DEACTIVATED", student, Map.of(), now);
        return detail(student, now);
    }

    @Transactional
    public PasswordResetResult resetPassword(TenantContext tenant, String publicId, Actor actor) {
        StudentJpaEntity student = findScopedForUpdate(tenant, publicId);
        String temporaryPassword = generateValidTemporaryPassword(student.getEmail());
        Instant now = clock.instant();
        student.resetPassword(passwordHasher.encode(temporaryPassword),
                now.plus(properties.getSecurity().getTemporaryPasswordDuration()), actor.userId(), now);
        studentRepository.save(student);
        revokeSessions(student.getId(), StudentSessionRevocationReason.PASSWORD_RESET, now);
        audit(actor, "STUDENT_PASSWORD_RESET", student, Map.of("oneTimeDisplay", true), now);
        return new PasswordResetResult(detail(student, now), temporaryPassword);
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(TenantContext tenant, String publicId) {
        StudentJpaEntity student = findScoped(tenant, publicId);
        return sessionRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream().map(this::sessionView).toList();
    }

    @Transactional
    public void revokeSession(TenantContext tenant, String publicId, String sessionPublicId, Actor actor) {
        StudentJpaEntity student = findScoped(tenant, publicId);
        StudentSessionJpaEntity session = sessionRepository.findByPublicIdAndStudentId(sessionPublicId, student.getId())
                .orElseThrow(() -> new BusinessException("STUDENT_SESSION_NOT_FOUND", "La sesión no existe."));
        Instant now = clock.instant();
        if (session.getStatus() == StudentSessionStatus.ACTIVE) {
            session.revoke(StudentSessionRevocationReason.ADMIN_REVOKED, now);
            sessionRepository.save(session);
        }
        audit(actor, "STUDENT_SESSION_REVOKED", student, Map.of("sessionPublicId", sessionPublicId), now);
    }

    @Transactional
    public void revokeAllSessions(TenantContext tenant, String publicId, Actor actor) {
        StudentJpaEntity student = findScoped(tenant, publicId);
        Instant now = clock.instant();
        revokeSessions(student.getId(), StudentSessionRevocationReason.ADMIN_REVOKED, now);
        audit(actor, "STUDENT_SESSIONS_REVOKED", student, Map.of(), now);
    }

    StudentJpaEntity findScopedForUpdate(TenantContext tenant, String publicId) {
        return studentRepository.findByOrganizationIdAndPublicIdForUpdate(requireOrganization(tenant), publicId)
                .filter(student -> student.getStatus() != StudentStatus.DELETED)
                .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
    }

    StudentJpaEntity findScopedEntity(TenantContext tenant, String publicId) {
        return findScoped(tenant, publicId);
    }

    private void revokeSessions(Long studentId, StudentSessionRevocationReason reason, Instant now) {
        sessionRepository.revokeActive(studentId, StudentSessionStatus.ACTIVE, StudentSessionStatus.REVOKED, reason, now);
    }

    private StudentSessionRevocationReason revocationReason(StudentEffectiveStatus status) {
        return status == StudentEffectiveStatus.EXPIRED ? StudentSessionRevocationReason.EXPIRED
                : status == StudentEffectiveStatus.DELETED ? StudentSessionRevocationReason.DELETED
                : StudentSessionRevocationReason.DEACTIVATED;
    }

    private StudentJpaEntity findScoped(TenantContext tenant, String publicId) {
        return studentRepository.findByOrganizationIdAndPublicId(requireOrganization(tenant), publicId)
                .filter(student -> student.getStatus() != StudentStatus.DELETED)
                .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "El estudiante no existe."));
    }

    Long requireOrganization(TenantContext tenant) {
        if (tenant == null || !tenant.hasOrganization() || tenant.globalScope()) {
            throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED",
                    "Selecciona una organización comercial para administrar colaboradores.",
                    Map.of("organizationPublicId", "Debes seleccionar una organización comercial."));
        }
        OrganizationJpaEntity organization = organizationRepository.findById(tenant.organizationId())
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe.",
                        Map.of("organizationPublicId", "La organización seleccionada no existe.")));
        if (organization.isGlobal()) {
            throw new BusinessException("STUDENT_GLOBAL_FORBIDDEN",
                    "Los colaboradores no pueden administrarse dentro de GLOBAL.");
        }
        return organization.getId();
    }

    private Long requireOperationalOrganization(TenantContext tenant) {
        return requireOperationalOrganizationEntity(tenant).getId();
    }

    private OrganizationJpaEntity requireOperationalOrganizationEntity(TenantContext tenant) {
        Long organizationId = requireOrganization(tenant);
        OrganizationJpaEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
        if (!organization.isOperational(LocalDate.now(clock))) {
            throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL",
                    "La organización debe estar activa y vigente para activar estudiantes.",
                    Map.of("organizationPublicId", "Selecciona una organización comercial activa y vigente."));
        }
        return organization;
    }

    private void validateRequired(CreateCommand command) {
        if (command == null) throw new BusinessException("VALIDATION_ERROR", "La solicitud contiene datos inválidos.");
        if (blank(command.displayName()) && blank(command.firstName()) && blank(command.lastName())) {
            throw fieldError("STUDENT_NAME_REQUIRED", "El nombre completo es obligatorio.",
                    "displayName", "El nombre completo del colaborador es obligatorio.");
        }
        if (blank(command.email())) throw fieldError("STUDENT_EMAIL_REQUIRED", "El correo es obligatorio.", "email", "El correo electrónico es obligatorio.");
    }

    private void validateUpdateRequired(UpdateCommand command) {
        if (command == null) throw new BusinessException("VALIDATION_ERROR", "La solicitud contiene datos inválidos.");
        if (blank(command.displayName()) && blank(command.firstName()) && blank(command.lastName())) {
            throw fieldError("STUDENT_NAME_REQUIRED", "El nombre completo es obligatorio.",
                    "displayName", "El nombre completo del colaborador es obligatorio.");
        }
        if (blank(command.email())) throw fieldError("STUDENT_EMAIL_REQUIRED", "El correo es obligatorio.", "email", "El correo electrónico es obligatorio.");
    }

    private void validateDates(LocalDate validFrom, LocalDate expiresAt) {
        if (validFrom == null) throw fieldError("STUDENT_VALID_FROM_REQUIRED", "El inicio de vigencia es obligatorio.", "validFrom", "El inicio de vigencia es obligatorio.");
        if (expiresAt == null) throw fieldError("STUDENT_EXPIRES_AT_REQUIRED", "La fecha de vencimiento es obligatoria.", "expiresAt", "La fecha de vencimiento es obligatoria.");
        if (expiresAt.isBefore(validFrom)) {
            throw fieldError("STUDENT_DATES_INVALID", "Las fechas de vigencia no son válidas.",
                    "expiresAt", "La fecha de vencimiento no puede ser anterior al inicio de vigencia.");
        }
    }

    private String generateValidTemporaryPassword(String email) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = passwordGenerator.generate();
            try {
                passwordPolicy.validate(candidate, email);
                return candidate;
            } catch (BusinessException exception) {
                if (!"PASSWORD_CONTAINS_EMAIL".equals(exception.getCode())) throw exception;
            }
        }
        throw new BusinessException("TEMPORARY_PASSWORD_GENERATION_FAILED",
                "No fue posible generar una contraseña temporal segura. Intenta nuevamente.");
    }


    private void validateVersion(StudentJpaEntity student, Long version) {
        if (version == null || !version.equals(student.getVersion())) {
            throw new BusinessException("STUDENT_VERSION_CONFLICT", "El estudiante fue modificado por otra operación. Actualiza la página.");
        }
    }

    private String provisionalStudentCode(String organizationName, String publicId) {
        String suffix = publicId.replace("-", "").toUpperCase(java.util.Locale.ROOT);
        return organizationPrefix(organizationName) + "00" + suffix.substring(0, Math.min(16, suffix.length()));
    }

    private String generateAvailableStudentCode(String organizationName, Long organizationId, Long studentId) {
        for (int attempt = 0; attempt < 100; attempt++) {
            int randomDigits = ThreadLocalRandom.current().nextInt(100);
            String candidate = organizationPrefix(organizationName)
                    + String.format(java.util.Locale.ROOT, "%02d", randomDigits) + studentId;
            if (!studentRepository.existsByOrganizationIdAndStudentCode(organizationId, candidate)) return candidate;
        }
        throw new BusinessException("STUDENT_CODE_GENERATION_FAILED",
                "No fue posible generar un Código a nivel organización único. Intenta nuevamente.");
    }

    static String organizationPrefix(String organizationName) {
        String normalized = Normalizer.normalize(organizationName == null ? "" : organizationName,
                Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
        String prefix = normalized.length() >= 2 ? normalized.substring(0, 2) : normalized;
        return (prefix + "XX").substring(0, 2);
    }

    private String cleanStudentCode(String value) {
        if (blank(value)) return null;
        String cleaned = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (cleaned.length() > 80 || !cleaned.matches("[A-Z0-9_-]+")) {
            throw fieldError("STUDENT_CODE_INVALID", "El Código a nivel organización no es válido.",
                    "studentCode", "Utiliza únicamente letras, números, guion o guion bajo, con máximo 80 caracteres.");
        }
        return cleaned;
    }

    private void validateStudentCodeAvailable(Long organizationId, Long currentStudentId, String code) {
        studentRepository.findByOrganizationIdAndStudentCode(organizationId, code)
                .filter(existing -> currentStudentId == null || !existing.getId().equals(currentStudentId))
                .ifPresent(existing -> { throw fieldError("STUDENT_CODE_EXISTS",
                        "El Código a nivel organización ya está asignado a otro colaborador.",
                        "studentCode", "Captura un código diferente."); });
    }

    private String cleanOptional(String value) {
        return blank(value) ? null : value.trim();
    }

    private String normalizeCorporateUser(String value) {
        if (value == null) return null;
        if (value.length() > 100) {
            throw fieldError("STUDENT_CORPORATE_USER_INVALID", "El Usuario corporativo no es válido.",
                    "corporateUser", "El Usuario corporativo no puede superar 100 caracteres.");
        }
        return value.toUpperCase(java.util.Locale.ROOT);
    }

    private void validateCorporateUser(Long currentStudentId, LocalDate admissionDate,
            String corporateUser, String normalizedCorporateUser) {
        validateCorporateUser(currentStudentId, admissionDate, corporateUser, normalizedCorporateUser, false);
    }

    private void validateCorporateUser(Long currentStudentId, LocalDate admissionDate,
            String corporateUser, String normalizedCorporateUser, boolean existingValuePreserved) {
        if (admissionDate == null && corporateUser != null && !existingValuePreserved) {
            throw fieldError("STUDENT_CORPORATE_USER_REQUIRES_ADMISSION_DATE",
                    "Captura una Fecha de alta para registrar el Usuario corporativo.",
                    "corporateUser", "El Usuario corporativo solo puede capturarse cuando existe Fecha de alta.");
        }
        if (normalizedCorporateUser == null) return;
        studentRepository.findByNormalizedCorporateUser(normalizedCorporateUser)
                .filter(existing -> currentStudentId == null || !existing.getId().equals(currentStudentId))
                .ifPresent(existing -> { throw fieldError("STUDENT_CORPORATE_USER_EXISTS",
                        "El Usuario corporativo ya está asignado a otro colaborador.",
                        "corporateUser", "Captura un usuario diferente."); });
    }

    private BusinessException fieldError(String code, String message, String field, String fieldMessage) {
        return new BusinessException(code, message, Map.of(field, fieldMessage));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String cleanNamePart(String value) { return value == null ? "" : value.trim(); }
    private String normalizeQuery(String query) { return blank(query) ? null : query.trim(); }

    private ResolvedName resolveName(String firstName, String lastName, String displayName) {
        String cleanFirst = cleanNamePart(firstName);
        String cleanLast = cleanNamePart(lastName);
        String cleanDisplay = !blank(displayName) ? displayName.trim()
                : (cleanFirst + " " + cleanLast).trim();
        if (cleanFirst.isBlank()) {
            String[] parts = cleanDisplay.split("\s+", 2);
            cleanFirst = parts[0];
            if (cleanLast.isBlank() && parts.length > 1) cleanLast = parts[1];
        }
        if (cleanLast.isBlank()) cleanLast = "-";
        return new ResolvedName(cleanFirst, cleanLast, cleanDisplay);
    }

    private void audit(Actor actor, String event, StudentJpaEntity student, Map<String, Object> extra, Instant now) {
        java.util.HashMap<String, Object> data = new java.util.HashMap<>(extra);
        data.put("studentPublicId", student.getPublicId());
        data.put("organizationId", student.getOrganizationId());
        auditLogPort.record(actor.userId(), event, "STUDENTS", event, actor.ipAddress(), actor.userAgent(), data, now);
    }

    private StudentSummary summary(StudentJpaEntity student, Instant now) {
        return new StudentSummary(student.getPublicId(), student.getStudentCode(), student.getCorporateUser(), student.getEmail(), student.getDisplayName(),
                student.getStatus(), student.effectiveStatusOn(LocalDate.now(clock)), student.getValidFrom(), student.getExpiresAt(),
                student.getLastLoginAt(), student.getUpdatedAt(), student.getVersion());
    }

    private StudentDetail detail(StudentJpaEntity student, Instant now) {
        return new StudentDetail(student.getPublicId(), student.getStudentCode(), student.getCorporateUser(), student.getEmail(), student.getFirstName(),
                student.getLastName(), student.getDisplayName(), student.getStatus(), student.effectiveStatusOn(LocalDate.now(clock)),
                student.getValidFrom(), student.getExpiresAt(), student.isPasswordChangeRequired(),
                student.getTemporaryPasswordExpiresAt(), student.getLastLoginAt(), student.getCreatedAt(),
                student.getUpdatedAt(), student.getVersion());
    }

    private SessionView sessionView(StudentSessionJpaEntity session) {
        return new SessionView(session.getPublicId(), session.getStatus(), session.getIpAddress(), session.getUserAgent(),
                session.getCreatedAt(), session.getLastActivityAt(), session.getExpiresAt(), session.getRevokedAt(),
                session.getRevocationReason() == null ? null : session.getRevocationReason().name());
    }

    private record ResolvedName(String firstName, String lastName, String displayName) {}

    public record Actor(Long userId, String ipAddress, String userAgent) {}
    public record CreateCommand(String email, String firstName, String lastName,
            String displayName, StudentStatus status, LocalDate validFrom, LocalDate expiresAt,
            LocalDate admissionDate, String studentCode, String corporateUser) {
        public CreateCommand(String email, String firstName, String lastName, String displayName,
                StudentStatus status, LocalDate validFrom, LocalDate expiresAt) {
            this(email, firstName, lastName, displayName, status, validFrom, expiresAt, null, null, null);
        }
    }
    public record UpdateCommand(String email, String firstName, String lastName, String displayName,
            LocalDate validFrom, LocalDate expiresAt, LocalDate admissionDate, String studentCode,
            String corporateUser, Long version) {
        public UpdateCommand(String email, String firstName, String lastName, String displayName,
                LocalDate validFrom, LocalDate expiresAt, Long version) {
            this(email, firstName, lastName, displayName, validFrom, expiresAt, null, null, null, version);
        }
    }
    public record StudentSummary(String publicId, String studentCode, String corporateUser, String email, String displayName,
            StudentStatus status, StudentEffectiveStatus effectiveStatus, LocalDate validFrom, LocalDate expiresAt,
            Instant lastLoginAt, Instant updatedAt, Long version) {}
    public record StudentDetail(String publicId, String studentCode, String corporateUser, String email, String firstName, String lastName,
            String displayName, StudentStatus status, StudentEffectiveStatus effectiveStatus, LocalDate validFrom,
            LocalDate expiresAt, boolean passwordChangeRequired, Instant temporaryPasswordExpiresAt,
            Instant lastLoginAt, Instant createdAt, Instant updatedAt, Long version) {}
    public record CreateResult(StudentDetail student, String temporaryPassword) {}
    public record PasswordResetResult(StudentDetail student, String temporaryPassword) {}
    public record SessionView(String publicId, StudentSessionStatus status, String ipAddress, String userAgent,
            Instant createdAt, Instant lastActivityAt, Instant expiresAt, Instant revokedAt, String revocationReason) {}
    public record PageResult(List<StudentSummary> content, int page, int size, long totalElements, int totalPages) {
        public PageResult {
            content = content == null ? List.of() : List.copyOf(content);
            page = Math.max(page, 0); size = Math.max(size, 1);
            totalElements = Math.max(totalElements, 0); totalPages = Math.max(totalPages, 0);
        }
        public static PageResult empty(int page, int size) { return new PageResult(List.of(), page, size, 0, 0); }
    }
}
