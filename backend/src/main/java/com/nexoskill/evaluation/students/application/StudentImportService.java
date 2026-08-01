package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.application.CertificationModels;
import com.nexoskill.evaluation.certifications.application.StudentCertificationService;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationLevel;
import com.nexoskill.evaluation.certifications.domain.CertificationLifecycleCalculator;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.application.importing.XlsxCertificationReader;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StudentImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudentImportService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    private static final int TOKEN_MINUTES = 30;
    private static final Set<String> ORGANIZATION_OPERATOR_ROLES = Set.of("MANAGER", "SUPERVISOR");
    private static final String GLOBAL_ADMINISTRATOR_ROLE = "ADMINISTRATOR";
    private static final List<String> CERT_TYPES = List.of(
            "TECHNOLOGICAL", "DEVELOPMENT_SECURITY", "NORMATIVE_TESTING", "ONE", "AGILE");

    private final XlsxCertificationReader reader;
    private final StudentFoundationService foundation;
    private final StudentService studentService;
    private final StudentExperienceService experienceService;
    private final StudentCertificationService certificationService;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final EntityManager entityManager;
    private final TransactionTemplate rowTransaction;
    private final Map<String, PendingImport> pending = new ConcurrentHashMap<>();
    private final Set<String> applying = ConcurrentHashMap.newKeySet();

    public StudentImportService(XlsxCertificationReader reader, StudentFoundationService foundation,
            StudentService studentService, StudentExperienceService experienceService,
            StudentCertificationService certificationService, NamedParameterJdbcTemplate jdbc, Clock clock,
            PlatformTransactionManager transactionManager, EntityManager entityManager) {
        this.reader = reader;
        this.foundation = foundation;
        this.studentService = studentService;
        this.experienceService = experienceService;
        this.certificationService = certificationService;
        this.jdbc = jdbc;
        this.clock = clock;
        this.entityManager = entityManager;
        this.rowTransaction = new TransactionTemplate(transactionManager);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public Preview preview(TenantContext tenant, AuthenticatedUser actor, String organizationPublicId,
            String fileName, byte[] content) {
        TenantContext effectiveTenant = resolveImportTenant(tenant, actor, organizationPublicId);
        cleanupExpired();
        if (content == null || content.length == 0) {
            throw new BusinessException("STUDENT_IMPORT_FILE_REQUIRED", "Selecciona un archivo Excel para continuar.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new BusinessException("STUDENT_IMPORT_FILE_TOO_LARGE",
                    "El archivo supera el límite de 50 MB permitido para la importación.");
        }
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("STUDENT_IMPORT_FILE_TYPE",
                    "El archivo debe tener formato .xlsx.");
        }

        String digest = sha256(content);
        Organization organization = organization(effectiveTenant.organizationId());
        XlsxCertificationReader.SheetData sheet = reader.read(new ByteArrayInputStream(content));
        Catalogs catalogs = catalogs(effectiveTenant);
        List<ExistingStudent> existing = existingStudents(effectiveTenant.organizationId());
        Map<Long, Map<String, CertificationData>> existingCertifications = existingCertifications(effectiveTenant.organizationId());
        Map<Long, ExperienceSnapshot> existingExperience = existingExperience(effectiveTenant.organizationId());

        List<ImportedStudent> parsed = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        for (XlsxCertificationReader.RowData row : sheet.rows()) {
            ParseResult result = parseRow(row, catalogs, organization);
            parsed.add(result.student());
            errors.addAll(result.errors());
        }

        Map<String, List<ImportedStudent>> fileKeys = new HashMap<>();
        for (ImportedStudent item : parsed) fileKeys.computeIfAbsent(item.matchKey(), ignored -> new ArrayList<>()).add(item);
        Set<String> duplicateRowKeys = new HashSet<>();
        List<Issue> conflicts = new ArrayList<>();
        fileKeys.values().stream().filter(values -> values.size() > 1).forEach(values -> {
            values.forEach(value -> duplicateRowKeys.add(value.rowKey()));
            conflicts.add(new Issue(values.getFirst().rowNumber(), "DUPLICATE_FILE_IDENTITY",
                    "El archivo contiene varias filas para " + values.getFirst().fullName()
                            + ". Revisa la identidad antes de continuar."));
        });

        Map<String, List<ExistingStudent>> byEmail = groupExisting(existing, true);
        Map<String, List<ExistingStudent>> byName = groupExisting(existing, false);
        Set<Long> matchedIds = new HashSet<>();
        Set<Long> referencedIds = new HashSet<>();
        List<NewStudentPreview> newStudents = new ArrayList<>();
        List<ChangedStudentPreview> changedStudents = new ArrayList<>();
        Map<String, Match> matches = new HashMap<>();

        for (ImportedStudent imported : parsed) {
            List<ExistingStudent> candidates = imported.normalizedEmail() == null
                    ? List.of() : byEmail.getOrDefault(imported.normalizedEmail(), List.of());
            if (candidates.isEmpty()) candidates = byName.getOrDefault(imported.normalizedName(), List.of());
            if (duplicateRowKeys.contains(imported.rowKey()) || imported.hasBlockingErrors()) {
                // Una fila inválida no puede actualizar, pero sí debe evitar una posible baja falsa
                // cuando identifica de forma segura a un colaborador ya existente.
                candidates.forEach(candidate -> referencedIds.add(candidate.id()));
                continue;
            }
            if (candidates.size() > 1) {
                candidates.forEach(candidate -> referencedIds.add(candidate.id()));
                conflicts.add(new Issue(imported.rowNumber(), "AMBIGUOUS_IDENTITY",
                        "Existen varios colaboradores que coinciden con " + imported.fullName()
                                + ". No se aplicará ningún cambio automáticamente."));
                continue;
            }
            if (candidates.isEmpty()) {
                newStudents.add(new NewStudentPreview(imported.rowKey(), imported.rowNumber(), imported.fullName(),
                        imported.profileName(), imported.primaryTechnology(), imported.email(), imported.warnings()));
                matches.put(imported.rowKey(), new Match(imported, null));
                continue;
            }
            ExistingStudent current = candidates.getFirst();
            referencedIds.add(current.id());
            if (!matchedIds.add(current.id())) {
                conflicts.add(new Issue(imported.rowNumber(), "DUPLICATE_MATCH",
                        "Más de una fila intenta actualizar al colaborador " + current.displayName() + "."));
                continue;
            }
            if (current.primaryTechnology() != null && imported.primaryTechnology() != null
                    && !StudentExperienceService.normalizeKey(current.primaryTechnology())
                            .equals(StudentExperienceService.normalizeKey(imported.primaryTechnology()))) {
                conflicts.add(new Issue(imported.rowNumber(), "PRIMARY_TECHNOLOGY_CONFLICT",
                        "La tecnología principal actual de " + current.displayName() + " es "
                                + current.primaryTechnology() + " y el Excel propone " + imported.primaryTechnology()
                                + ". Revisa el cambio manualmente."));
                continue;
            }
            List<FieldChange> changes = compare(current, imported,
                    existingCertifications.getOrDefault(current.id(), Map.of()),
                    existingExperience.getOrDefault(current.id(), ExperienceSnapshot.empty()));
            matches.put(imported.rowKey(), new Match(imported, current));
            if (!changes.isEmpty()) {
                changedStudents.add(new ChangedStudentPreview(current.publicId(), imported.rowKey(),
                        current.displayName(), changes, imported.warnings()));
            }
        }

        List<PossibleLowPreview> possibleLows = existing.stream()
                .filter(value -> "ACTIVE".equals(value.status()) && !referencedIds.contains(value.id()))
                .sorted(Comparator.comparing(ExistingStudent::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(value -> new PossibleLowPreview(value.publicId(), value.displayName(), value.email(), "KEEP"))
                .toList();

        String token = UUID.randomUUID().toString();
        Set<String> previewNewRows = newStudents.stream().map(NewStudentPreview::rowKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, Set<String>> previewChangeFields = changedStudents.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(ChangedStudentPreview::studentPublicId,
                        value -> value.changes().stream().map(FieldChange::key)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())));
        Set<String> previewPossibleLows = possibleLows.stream().map(PossibleLowPreview::studentPublicId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        PendingImport state = new PendingImport(token, actor.internalId(), effectiveTenant.organizationId(), digest,
                java.time.Instant.now(clock).plusSeconds(TOKEN_MINUTES * 60L), organization, parsed,
                Map.copyOf(matches), previewNewRows, previewChangeFields, previewPossibleLows,
                List.copyOf(errors), List.copyOf(conflicts));
        pending.put(token, state);
        return new Preview(token, fileName, sheet.sheetName(), organization.name(), organization.code(),
                sheet.rows().size(), newStudents, changedStudents, possibleLows, conflicts, errors,
                "La vista previa caduca en " + TOKEN_MINUTES + " minutos y ningún cambio ha sido aplicado.");
    }

    public ApplyResult apply(TenantContext tenant, AuthenticatedUser actor, ApplyCommand command,
            StudentService.Actor requestActor) {
        if (command == null || command.token() == null || command.token().isBlank()) {
            throw new BusinessException("STUDENT_IMPORT_TOKEN_REQUIRED", "La vista previa ya no es válida.");
        }
        String normalizedToken = command.token().trim();
        PendingImport state = pending.get(normalizedToken);
        if (state == null) {
            throw new BusinessException("STUDENT_IMPORT_TOKEN_UNAVAILABLE",
                    "La vista previa ya no está disponible. Puede haber sido aplicada, descartada o invalidada por un reinicio del backend. Vuelve a validar el archivo.");
        }
        if (state.expiresAt().isBefore(java.time.Instant.now(clock))) {
            pending.remove(normalizedToken, state);
            throw new BusinessException("STUDENT_IMPORT_TOKEN_EXPIRED",
                    "La vista previa caducó después de " + TOKEN_MINUTES + " minutos. Vuelve a validar el archivo.");
        }
        TenantContext effectiveTenant = effectiveTenantForState(tenant, actor, state);

        Map<String, NewSelection> newSelections = indexNew(command.newStudents());
        Map<String, ChangeSelection> changeSelections = indexChanges(command.changedStudents());
        Map<String, LowSelection> lowSelections = indexLows(command.possibleLows());
        if (!applying.add(normalizedToken)) {
            throw new BusinessException("STUDENT_IMPORT_APPLY_IN_PROGRESS",
                    "La importación ya se está aplicando. Espera a que termine antes de volver a confirmar.");
        }
        try {
            validateSelectedEmails(effectiveTenant.organizationId(), state, newSelections);
            validateSelections(state, newSelections, changeSelections, lowSelections);
            if (pending.get(normalizedToken) != state) {
                throw new BusinessException("STUDENT_IMPORT_TOKEN_UNAVAILABLE",
                        "La vista previa fue descartada antes de iniciar la importación. Vuelve a validar el archivo.");
            }
            List<ExistingStudent> lowCandidates = existingStudents(effectiveTenant.organizationId());
            List<Credential> credentials = new ArrayList<>();
            List<Issue> applyErrors = new ArrayList<>();
            int created = 0;
            int updated = 0;
            int deactivated = 0;
            for (ImportedStudent imported : state.imported()) {
                Match match = state.matches().get(imported.rowKey());
                if (match == null || imported.hasBlockingErrors()) continue;
                try {
                    RowOutcome outcome = rowTransaction.execute(status -> applyRow(effectiveTenant, state, imported, match,
                            newSelections, changeSelections, requestActor, actor));
                    if (outcome == null) continue;
                    if (outcome.credential() != null) credentials.add(outcome.credential());
                    created += outcome.created();
                    updated += outcome.updated();
                } catch (RuntimeException exception) {
                    LOGGER.error("Student import failed for row {} in organization {}",
                            imported.rowNumber(), effectiveTenant.organizationId(), exception);
                    applyErrors.add(importFailure(imported.rowNumber(),
                            "Colaborador " + imported.fullName() + " (fila " + imported.rowNumber() + ")",
                            exception));
                }
            }
            for (ExistingStudent current : lowCandidates) {
                LowSelection selection = lowSelections.get(current.publicId());
                if (selection == null || !"DEACTIVATE".equals(selection.action())) continue;
                try {
                    rowTransaction.executeWithoutResult(status -> studentService.deactivate(
                            effectiveTenant, current.publicId(), requestActor));
                    deactivated++;
                } catch (RuntimeException exception) {
                    LOGGER.error("Student import deactivation failed for student {} in organization {}",
                            current.publicId(), effectiveTenant.organizationId(), exception);
                    applyErrors.add(importFailure(0,
                            "No fue posible desactivar a " + current.displayName(), exception));
                }
            }
            ApplyResult result = new ApplyResult(created, updated, deactivated, applyErrors, credentials,
                    credentials.isEmpty() ? null
                            : "Las contraseñas temporales se muestran una sola vez. Cópialas antes de cerrar esta vista.");
            pending.remove(normalizedToken, state);
            return result;
        } finally {
            applying.remove(normalizedToken);
        }
    }
    public void discard(TenantContext tenant, AuthenticatedUser actor, String token) {
        if (token == null || token.isBlank()) return;
        String normalizedToken = token.trim();
        PendingImport state = pending.get(normalizedToken);
        if (state == null) return;
        if (!applying.add(normalizedToken)) {
            throw new BusinessException("STUDENT_IMPORT_APPLY_IN_PROGRESS",
                    "La importación ya se está aplicando y no puede descartarse en este momento.");
        }
        try {
            effectiveTenantForState(tenant, actor, state);
            pending.remove(normalizedToken, state);
        } finally {
            applying.remove(normalizedToken);
        }
    }

    private RowOutcome applyRow(TenantContext tenant, PendingImport state, ImportedStudent imported, Match match,
            Map<String, NewSelection> newSelections, Map<String, ChangeSelection> changeSelections,
            StudentService.Actor requestActor, AuthenticatedUser actor) {
        if (match.existing() == null) {
            NewSelection selection = newSelections.get(imported.rowKey());
            if (selection == null || !selection.selected()) return RowOutcome.none();
            ImportedStudent resolved = materializeCatalogs(tenant.organizationId(), imported, actor.internalId(), null);
            String email = requireEmail(selection.email(), resolved.rowNumber());
            String ownerPublicId = tenant.globalAdministrator() ? state.organization().publicId() : null;
            StudentFoundationService.CreateResult result = foundation.create(tenant,
                    new StudentFoundationService.CreateCommand(ownerPublicId,
                            generatedStudentCode(state.digest(), resolved),
                            email, resolved.firstName(), resolved.lastName(), resolved.fullName(), StudentStatus.ACTIVE,
                            LocalDate.now(clock), accessExpiry(state.organization()), resolved.admissionDate(),
                            resolved.profilePublicId(), resolved.technologicalProfilePublicId(),
                            resolved.flag("TECHNOLOGICAL"), resolved.flag("DEVELOPMENT_SECURITY"),
                            resolved.flag("NORMATIVE_TESTING"), resolved.flag("ONE"), resolved.flag("AGILE")),
                    requestActor);
            Long studentId = studentId(result.student().publicId(), tenant.organizationId());
            synchronizeStudentFoundationForCertification(studentId, tenant.organizationId(),
                    result.student().publicId(), resolved.admissionDate());
            persistImportedDetails(tenant, result.student().publicId(), studentId, resolved, actor);
            return new RowOutcome(1, 0, new Credential(state.organization().name(), state.organization().code(),
                    resolved.fullName(), email, result.temporaryPassword()));
        }
        ChangeSelection selection = changeSelections.get(match.existing().publicId());
        if (selection == null || selection.fields().isEmpty()) return RowOutcome.none();
        ImportedStudent resolved = materializeCatalogs(tenant.organizationId(), imported, actor.internalId(),
                selection.fields());
        updateExisting(tenant, match.existing(), resolved, selection.fields(), requestActor, actor);
        return new RowOutcome(0, 1, null);
    }


    /**
     * StudentService persists the account with JPA and StudentFoundationService completes the
     * certification foundation with JDBC in the same row transaction. Flush first, reapply the
     * imported admission date with the full tenant key, clear the persistence context and reload
     * the entity before any certification deadline is calculated.
     */
    void synchronizeStudentFoundationForCertification(Long studentId, Long organizationId,
            String studentPublicId, LocalDate admissionDate) {
        if (studentId == null || organizationId == null || studentPublicId == null || studentPublicId.isBlank()) {
            throw new BusinessException("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED",
                    "No fue posible identificar al colaborador antes de aplicar sus certificaciones.");
        }
        if (admissionDate == null) {
            throw new BusinessException("STUDENT_IMPORT_ADMISSION_DATE_REQUIRED",
                    "La fecha de alta importada es obligatoria para aplicar las certificaciones.");
        }

        entityManager.flush();
        int updated = jdbc.update("""
            UPDATE STUDENT
               SET ADMISSION_DATE = :admissionDate
             WHERE STUDENT_ID = :studentId
               AND ORGANIZATION_ID = :organizationId
               AND PUBLIC_ID = :studentPublicId
            """, new MapSqlParameterSource()
                .addValue("admissionDate", java.sql.Date.valueOf(admissionDate), java.sql.Types.DATE)
                .addValue("studentId", studentId)
                .addValue("organizationId", organizationId)
                .addValue("studentPublicId", studentPublicId));
        if (updated != 1) {
            throw new BusinessException("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED",
                    "No fue posible sincronizar la fecha de alta antes de aplicar las certificaciones.");
        }

        entityManager.clear();
        StudentJpaEntity reloaded = entityManager.find(StudentJpaEntity.class, studentId);
        if (reloaded == null
                || !organizationId.equals(reloaded.getOrganizationId())
                || !studentPublicId.equals(reloaded.getPublicId())) {
            throw new BusinessException("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED",
                    "No fue posible volver a consultar al colaborador dentro de la organización destino.");
        }
        entityManager.refresh(reloaded);
        if (!admissionDate.equals(reloaded.getAdmissionDate())) {
            throw new BusinessException("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED",
                    "La fecha de alta no quedó disponible para calcular las certificaciones. No se aplicó la fila.");
        }
    }

    private void validateSelectedEmails(Long organizationId, PendingImport state,
            Map<String, NewSelection> selections) {
        Map<String, ImportedStudent> importedByKey = state.imported().stream()
                .collect(java.util.stream.Collectors.toMap(ImportedStudent::rowKey, value -> value, (first, second) -> first));
        Set<String> normalized = new LinkedHashSet<>();
        for (NewSelection selection : selections.values()) {
            if (selection == null || !selection.selected()) continue;
            ImportedStudent imported = importedByKey.get(selection.rowKey());
            int row = imported == null ? 0 : imported.rowNumber();
            String email = requireEmail(selection.email(), row);
            String key = email.toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw new BusinessException("STUDENT_IMPORT_EMAIL_DUPLICATE_SELECTION",
                        "El correo " + email + " está repetido entre los colaboradores seleccionados.");
            }
        }
        if (normalized.isEmpty()) return;
        List<String> emailList = List.copyOf(normalized);
        List<String> existing = new ArrayList<>();
        for (int start = 0; start < emailList.size() && existing.isEmpty(); start += 900) {
            List<String> batch = emailList.subList(start, Math.min(start + 900, emailList.size()));
            existing.addAll(jdbc.query("""
                SELECT LOWER(EMAIL) FROM STUDENT
                 WHERE ORGANIZATION_ID = :organizationId AND STATUS <> 'DELETED'
                   AND LOWER(EMAIL) IN (:emails)
                """, new MapSqlParameterSource("organizationId", organizationId).addValue("emails", batch),
                    (rs, rowNum) -> rs.getString(1)));
        }
        if (!existing.isEmpty()) {
            throw new BusinessException("STUDENT_IMPORT_EMAIL_IN_USE",
                    "El correo " + existing.getFirst() + " ya está utilizado por otro colaborador de la organización.");
        }
    }
    private Issue importFailure(int row, String action, RuntimeException exception) {
        Throwable current = exception;
        Throwable root = exception;
        BusinessException business = null;
        boolean dataConflict = false;
        int depth = 0;
        while (current != null && depth++ < 20) {
            root = current;
            if (business == null && current instanceof BusinessException found) business = found;
            if (current instanceof DataIntegrityViolationException) dataConflict = true;
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        String prefix = action == null || action.isBlank() ? "" : action + ": ";
        if (business != null) {
            return new Issue(row, business.getCode(), prefix + business.getMessage());
        }
        if (dataConflict) {
            return new Issue(row, "STUDENT_IMPORT_CONFLICT",
                    prefix + "los datos entran en conflicto con información existente.");
        }
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) detail = root.getClass().getSimpleName();
        detail = detail.replaceAll("\\s+", " ").trim();
        if (detail.length() > 500) detail = detail.substring(0, 500) + "…";
        return new Issue(row, "STUDENT_IMPORT_ROW_FAILED",
                prefix + "no fue posible completar la operación. Detalle técnico: " + detail);
    }

    private void updateExisting(TenantContext tenant, ExistingStudent current, ImportedStudent imported,
            Set<String> selectedFields, StudentService.Actor requestActor, AuthenticatedUser actor) {
        StudentFoundationService.StudentView detail = foundation.get(tenant, current.publicId());
        boolean identity = intersects(selectedFields, Set.of("name", "admissionDate", "profile", "technologicalProfile",
                "applies:TECHNOLOGICAL", "applies:DEVELOPMENT_SECURITY", "applies:NORMATIVE_TESTING",
                "applies:ONE", "applies:AGILE"));
        LocalDate effectiveAdmissionDate = selectedFields.contains("admissionDate")
                ? imported.admissionDate() : detail.admissionDate();
        if (identity) {
            foundation.update(tenant, current.publicId(), new StudentFoundationService.UpdateCommand(
                    detail.email(), selectedFields.contains("name") ? imported.firstName() : detail.firstName(),
                    selectedFields.contains("name") ? imported.lastName() : detail.lastName(),
                    selectedFields.contains("name") ? imported.fullName() : detail.displayName(),
                    detail.validFrom(), detail.expiresAt(), effectiveAdmissionDate,
                    selectedFields.contains("profile") ? imported.profilePublicId()
                            : detail.professionalProfile() == null ? null : detail.professionalProfile().publicId(),
                    selectedFields.contains("technologicalProfile") ? imported.technologicalProfilePublicId()
                            : detail.technologicalProfile() == null ? null : detail.technologicalProfile().publicId(),
                    selectedFields.contains("applies:TECHNOLOGICAL") ? imported.flag("TECHNOLOGICAL") : detail.appliesTechnologicalCertification(),
                    selectedFields.contains("applies:DEVELOPMENT_SECURITY") ? imported.flag("DEVELOPMENT_SECURITY") : detail.appliesDevelopmentSecurity(),
                    selectedFields.contains("applies:NORMATIVE_TESTING") ? imported.flag("NORMATIVE_TESTING") : detail.appliesNormativeTesting(),
                    selectedFields.contains("applies:ONE") ? imported.flag("ONE") : detail.appliesOne(),
                    selectedFields.contains("applies:AGILE") ? imported.flag("AGILE") : detail.appliesAgile(),
                    detail.version()), requestActor);
        }
        Long studentId = current.id();
        if (selectedFields.stream().anyMatch(field -> field.startsWith("experience:"))) {
            ExperienceSnapshot before = existingExperience(current.organizationId())
                    .getOrDefault(studentId, ExperienceSnapshot.empty());
            experienceService.replaceImported(current.organizationId(), studentId,
                    selectedFields.contains("experience:current") ? imported.currentTechnologies() : before.current(),
                    selectedFields.contains("experience:languages") ? imported.languages() : before.languages(),
                    selectedFields.contains("experience:known") ? imported.knownTechnologies() : before.known(),
                    actor.internalId());
        }
        boolean certificationChanges = selectedFields.stream().anyMatch(field -> field.startsWith("cert:"));
        if (identity || certificationChanges) {
            synchronizeStudentFoundationForCertification(studentId, current.organizationId(),
                    current.publicId(), effectiveAdmissionDate);
        }
        for (String type : CERT_TYPES) {
            if (selectedFields.stream().anyMatch(field -> field.startsWith("cert:" + type))) {
                CertificationData currentData = currentCertification(current.organizationId(), studentId, type);
                CertificationData selected = mergeCertification(currentData, imported.certifications().get(type),
                        selectedFields, type);
                upsertCertification(tenant, current.publicId(), selected, imported, actor);
            }
        }
    }

    private void persistImportedDetails(TenantContext tenant, String studentPublicId, Long studentId,
            ImportedStudent imported, AuthenticatedUser actor) {
        experienceService.replaceImported(tenant.organizationId(), studentId,
                imported.currentTechnologies(), imported.languages(), imported.knownTechnologies(), actor.internalId());
        CERT_TYPES.forEach(type -> upsertCertification(tenant, studentPublicId,
                imported.certifications().get(type), imported, actor));
    }

    private void upsertCertification(TenantContext tenant, String studentPublicId, CertificationData data,
            ImportedStudent imported, AuthenticatedUser actor) {
        if (data == null || !data.applies()) return;
        CertificationType type = CertificationType.valueOf(data.type());
        AuthenticatedUser certificationActor = certificationImportActor(actor, tenant);
        CertificationModels.StudentCertificationDetail detail = certificationService.get(
                tenant, studentPublicId, certificationActor);
        CertificationModels.CycleView existing = detail.cycles().stream()
                .filter(cycle -> cycle.type() == type && cycle.active())
                .findFirst()
                .orElseGet(() -> detail.cycles().stream().filter(cycle -> cycle.type() == type).findFirst().orElse(null));

        String technologyPublicId = null;
        CertificationLevel level = null;
        if (type == CertificationType.TECHNOLOGICAL) {
            technologyPublicId = resolveQuestionTechnologyPublicId(tenant.organizationId(), imported.primaryTechnology());
            if (technologyPublicId == null) {
                throw new BusinessException("STUDENT_IMPORT_TECHNOLOGY_NOT_FOUND",
                        "La tecnología principal '" + imported.primaryTechnology()
                                + "' no está disponible para la organización.");
            }
            level = parseCertificationLevel(imported.certificationLevel());
        }

        List<CertificationModels.AttemptCommand> attempts = attemptCommands(
                tenant, studentPublicId, existing, data, certificationActor);
        CertificationModels.CycleCommand command = new CertificationModels.CycleCommand(
                existing == null ? null : existing.publicId(), type, technologyPublicId, level,
                type == CertificationType.TECHNOLOGICAL,
                parseTrackingStatus(data.trackingStatus()),
                existing == null ? null : existing.scheduledDate(), data.applicationDate(), data.approved(),
                existing == null ? null : existing.actionsToTake(),
                existing == null ? null : existing.softtekManagement(),
                existing == null ? null : existing.observations(), true,
                existing == null ? null : existing.version(), attempts);

        CertificationModels.StudentCertificationDetail saved = certificationService.save(
                tenant, studentPublicId, new CertificationModels.SaveCommand(List.of(command)), certificationActor,
                null, null);
        CertificationModels.CycleView savedCycle = saved.cycles().stream()
                .filter(cycle -> existing != null ? cycle.publicId().equals(existing.publicId())
                        : cycle.type() == type && cycle.active())
                .findFirst().orElse(null);
        if (savedCycle != null && data.deadline() != null
                && !Objects.equals(savedCycle.deadlineDate(), data.deadline())) {
            overrideImportedDeadline(tenant.organizationId(), studentPublicId, savedCycle.publicId(),
                    savedCycle.deadlineDate(), data.deadline(), actor.internalId());
        }
    }

    private List<CertificationModels.AttemptCommand> attemptCommands(TenantContext tenant, String studentPublicId,
            CertificationModels.CycleView cycle, CertificationData data, AuthenticatedUser actor) {
        if (oneAgile(data.type()) || data.attempt() == null) return List.of();
        int expected = data.attempt();
        if (cycle == null) {
            if (expected != 1) {
                throw new BusinessException("STUDENT_IMPORT_ATTEMPT_GAP",
                        typeLabel(data.type()) + ": no se puede crear el intento " + expected
                                + " sin registrar primero los intentos anteriores.");
            }
            return List.of(new CertificationModels.AttemptCommand(null, null, data.applicationDate(),
                    parseExamStatus(data.internalExamStatus()), data.score10(), data.approved(),
                    data.certificationStatus(), null, null));
        }
        List<CertificationModels.AttemptView> existing = certificationService
                .attempts(tenant, studentPublicId, cycle.publicId(), 0, 100, actor).content();
        CertificationModels.AttemptView same = existing.stream()
                .filter(attempt -> attempt.attemptNumber() == expected).findFirst().orElse(null);
        int max = existing.stream().mapToInt(CertificationModels.AttemptView::attemptNumber).max().orElse(0);
        if (same == null && expected != max + 1) {
            throw new BusinessException("STUDENT_IMPORT_ATTEMPT_GAP",
                    typeLabel(data.type()) + ": el intento " + expected
                            + " no es consecutivo respecto del historial actual.");
        }
        return List.of(new CertificationModels.AttemptCommand(
                same == null ? null : same.publicId(), same == null ? null : same.scheduledDate(),
                data.applicationDate(), parseExamStatus(data.internalExamStatus()), data.score10(), data.approved(),
                data.certificationStatus(), same == null ? null : same.observations(),
                same == null ? null : same.version()));
    }

    private void overrideImportedDeadline(Long organizationId, String studentPublicId, String cyclePublicId,
            LocalDate previous, LocalDate requested, Long actorId) {
        MapSqlParameterSource params = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("studentPublicId", studentPublicId).addValue("cyclePublicId", cyclePublicId)
                .addValue("deadline", sqlDate(requested)).addValue("actorId", actorId)
                .addValue("historyPublicId", UUID.randomUUID().toString())
                .addValue("previousValue", previous == null ? null : "deadline=" + previous)
                .addValue("newValue", "deadline=" + requested);
        int updated = jdbc.update("""
            UPDATE STUDENT_CERTIFICATION_CYCLE c
               SET c.DEADLINE_DATE = :deadline, c.UPDATED_BY = :actorId,
                   c.UPDATED_AT = SYSTIMESTAMP, c.VERSION_NO = c.VERSION_NO + 1
             WHERE c.PUBLIC_ID = :cyclePublicId AND c.ORGANIZATION_ID = :organizationId
               AND c.STUDENT_ID = (SELECT s.STUDENT_ID FROM STUDENT s
                                    WHERE s.PUBLIC_ID = :studentPublicId
                                      AND s.ORGANIZATION_ID = :organizationId)
            """, params);
        if (updated != 1) {
            throw new BusinessException("STUDENT_IMPORT_DEADLINE_CONFLICT",
                    "No fue posible aplicar la fecha límite porque el ciclo cambió durante la importación.");
        }
        jdbc.update("""
            INSERT INTO STUDENT_CERTIFICATION_HISTORY
                (PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, STUDENT_CERTIFICATION_CYCLE_ID,
                 EVENT_TYPE, PREVIOUS_VALUES, NEW_VALUES, REASON, CHANGED_BY, CHANGED_AT)
            SELECT :historyPublicId, s.STUDENT_ID, :organizationId,
                   c.STUDENT_CERTIFICATION_CYCLE_ID, 'IMPORT_DEADLINE_UPDATED',
                   :previousValue, :newValue, 'Carga masiva confirmada', :actorId, SYSTIMESTAMP
              FROM STUDENT s JOIN STUDENT_CERTIFICATION_CYCLE c ON c.STUDENT_ID = s.STUDENT_ID
             WHERE s.PUBLIC_ID = :studentPublicId AND s.ORGANIZATION_ID = :organizationId
               AND c.PUBLIC_ID = :cyclePublicId
            """, params);
    }

    private CertificationData mergeCertification(CertificationData current, CertificationData imported,
            Set<String> selectedFields, String type) {
        if (imported == null) return current;
        if (current == null) return imported;
        String prefix = "cert:" + type + ":";
        return new CertificationData(type,
                imported.applies(),
                selectedFields.contains(prefix + "status") ? imported.certificationStatus() : current.certificationStatus(),
                selectedFields.contains(prefix + "examStatus") ? imported.examStatus() : current.examStatus(),
                selectedFields.contains(prefix + "examStatus") ? imported.internalExamStatus() : current.internalExamStatus(),
                selectedFields.contains(prefix + "applicationDate") || selectedFields.contains(prefix + "lifecycle")
                        ? imported.applicationDate() : current.applicationDate(),
                selectedFields.contains(prefix + "score") ? imported.score10() : current.score10(),
                selectedFields.contains(prefix + "attempt") ? imported.attempt() : current.attempt(),
                selectedFields.contains(prefix + "deadline") ? imported.deadline() : current.deadline(),
                selectedFields.contains(prefix + "applicationDate") || selectedFields.contains(prefix + "status")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.expiration() : current.expiration(),
                selectedFields.contains(prefix + "status") || selectedFields.contains(prefix + "examStatus")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.approved() : current.approved(),
                selectedFields.contains(prefix + "status") || selectedFields.contains(prefix + "applicationDate")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.validityStatus() : current.validityStatus(),
                selectedFields.contains(prefix + "status") || selectedFields.contains(prefix + "examStatus")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.trackingStatus() : current.trackingStatus());
    }

    private CertificationData currentCertification(Long organizationId, Long studentId, String type) {
        return existingCertifications(organizationId).getOrDefault(studentId, Map.of()).get(type);
    }

    private CertificationLevel parseCertificationLevel(String value) {
        try {
            return CertificationLevel.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BusinessException("STUDENT_IMPORT_CERTIFICATION_LEVEL_INVALID",
                    "No fue posible determinar el nivel JR, STD o SR de la certificación tecnológica.");
        }
    }

    private CertificationTrackingStatus parseTrackingStatus(String value) {
        try {
            return value == null ? CertificationTrackingStatus.PENDING : CertificationTrackingStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return CertificationTrackingStatus.PENDING;
        }
    }

    private CertificationExamStatus parseExamStatus(String value) {
        try {
            return value == null ? CertificationExamStatus.NOT_SCHEDULED : CertificationExamStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return CertificationExamStatus.NOT_SCHEDULED;
        }
    }

    private String resolveQuestionTechnologyPublicId(Long organizationId, String name) {
        if (name == null) return null;
        String key = StudentExperienceService.normalizeKey(name);
        List<String> values = jdbc.query("""
            SELECT PUBLIC_ID FROM QUESTION_TECHNOLOGY
             WHERE STATUS = 'ACTIVE' AND CONTENT_SCOPE = 'ORGANIZATION'
               AND OWNER_ORGANIZATION_ID = :organizationId
               AND (UPPER(REGEXP_REPLACE(TRANSLATE(TECHNOLOGY_NAME,
                    'ÁÉÍÓÚÜÑáéíóúüñ','AEIOUUNaeiouun'), '[^A-Z0-9]+', ' ')) = :key
                    OR UPPER(REGEXP_REPLACE(TRANSLATE(TECHNOLOGY_CODE,
                    'ÁÉÍÓÚÜÑáéíóúüñ','AEIOUUNaeiouun'), '[^A-Z0-9]+', ' ')) = :key)
             ORDER BY TECHNOLOGY_ID
             FETCH FIRST 1 ROWS ONLY
            """, Map.of("organizationId", organizationId, "key", key), (rs, rowNum) -> rs.getString(1));
        return values.isEmpty() ? null : values.getFirst();
    }

    private ParseResult parseRow(XlsxCertificationReader.RowData row, Catalogs catalogs, Organization organization) {
        List<Issue> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String fullName = normalizedText(value(row, "NOMBRE EXTERNO"), 250);
        String profile = normalizedText(value(row, "PERFIL"), 200);
        String technology = normalizedText(value(row, "TECNOLOGÍA EN LA QUE SE CERTIFICA"), 200);
        String technologicalProfile = normalizedText(value(row, "PERFIL TECNOLOGICO"), 200);
        LocalDate admission = parseDate(value(row, "FECHA DE ALTA"), "FECHA DE ALTA", row.rowNumber(), errors, true);
        String email = firstValue(row, "CORREO", "EMAIL", "CORREO ELECTRONICO", "CORREO ELECTRÓNICO");
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        if (email != null && !EMAIL.matcher(email).matches()) {
            errors.add(new Issue(row.rowNumber(), "STUDENT_IMPORT_EMAIL_INVALID", "El correo del Excel no tiene un formato válido."));
        }
        if (fullName == null) errors.add(new Issue(row.rowNumber(), "STUDENT_IMPORT_NAME_REQUIRED", "NOMBRE EXTERNO es obligatorio."));
        if (profile == null) errors.add(new Issue(row.rowNumber(), "STUDENT_IMPORT_PROFILE_REQUIRED", "PERFIL es obligatorio."));
        if (technology == null) errors.add(new Issue(row.rowNumber(), "STUDENT_IMPORT_TECHNOLOGY_REQUIRED", "TECNOLOGÍA EN LA QUE SE CERTIFICA es obligatoria."));
        if (technologicalProfile == null) errors.add(new Issue(row.rowNumber(), "STUDENT_IMPORT_TECH_PROFILE_REQUIRED", "PERFIL TECNOLOGICO es obligatorio."));
        NameParts names = splitName(fullName, row.rowNumber(), errors);
        CatalogRef profileRef = resolveCatalog(catalogs.profiles(), profile);
        CatalogRef techProfileRef = resolveCatalog(catalogs.technologicalProfiles(), technologicalProfile);
        if (profile != null && profileRef == null) {
            warnings.add("El perfil '" + profile
                    + "' se creará en los catálogos de la organización al confirmar.");
        }
        if (technologicalProfile != null && techProfileRef == null) {
            warnings.add("El perfil tecnológico '" + technologicalProfile
                    + "' se creará en los catálogos de la organización al confirmar.");
        }

        Map<String, CertificationData> certifications = new LinkedHashMap<>();
        certifications.put("DEVELOPMENT_SECURITY", certification(row, "DEVELOPMENT_SECURITY", "¿APLICA DS?",
                "ESTATUS CERTIFICACIÓN DS", "ESTATUS DEL EXAMEN DS", "FECHA DE APLICACIÓN DS",
                "PROMEDIO DS", "INTENTO DS", null, admission, catalogs.policy("DEVELOPMENT_SECURITY"),
                row.rowNumber(), errors, warnings));
        certifications.put("TECHNOLOGICAL", certification(row, "TECHNOLOGICAL", "¿APLICA TECNOLOGICA?",
                "ESTATUS CERTIFICACIÓN", "ESTATUS DEL EXAMEN", "FECHA DE APLICACIÓN TEC",
                "PROMEDIO", "INTENTO", null, admission, catalogs.policy("TECHNOLOGICAL"),
                row.rowNumber(), errors, warnings));
        certifications.put("NORMATIVE_TESTING", certification(row, "NORMATIVE_TESTING", "¿APLICA NORMATIVA?",
                "ESTATUS CERTIFICACIÓN NORMATIVA", "ESTATUS DEL EXAMEN NORMATIVA", "FECHA DE APLICACIÓN NORMATIVA",
                "PROMEDIO NORMATIVA", null, "LIMITE PARA NORMATIVA", admission, catalogs.policy("NORMATIVE_TESTING"),
                row.rowNumber(), errors, warnings));
        certifications.put("ONE", certification(row, "ONE", "¿APLICA ONE?", "ESTATUS CERTIFICACIÓN ONE",
                null, null, null, null, null, admission, catalogs.policy("ONE"),
                row.rowNumber(), errors, warnings));
        certifications.put("AGILE", certification(row, "AGILE", "¿APLICA AGILE?", "ESTATUS CERTIFICACIÓN AGILE",
                null, null, null, null, null, admission, catalogs.policy("AGILE"),
                row.rowNumber(), errors, warnings));

        if (!organization.appliesCertifications() && certifications.values().stream().anyMatch(CertificationData::applies)) {
            errors.add(new Issue(row.rowNumber(), "STUDENT_CERTIFICATIONS_NOT_ENABLED",
                    "La organización no tiene habilitada la gestión de certificaciones."));
        }
        if (technology != null && resolveQuestionTechnologyPublicId(organization.id(), technology) == null) {
            warnings.add("La tecnología principal '" + technology
                    + "' se creará en los catálogos de la organización al confirmar.");
        }

        List<StudentExperienceService.ImportedItem> current = parseExperience(
                valueStarting(row, "TECNOLOGIA EN LA QUE DESARROLLA ACTUALMENTE"));
        List<StudentExperienceService.ImportedItem> languages = parseExperience(valueStarting(row, "LENGUAJES"));
        List<StudentExperienceService.ImportedItem> known = parseExperience(valueStarting(row, "TECNOLOGIAS CONOCIDAS"));
        String rowKey = "ROW-" + row.rowNumber();
        String normalizedName = StudentExperienceService.normalizeKey(fullName);
        String normalizedEmail = email == null ? null : email.toLowerCase(Locale.ROOT);
        String matchKey = normalizedEmail != null ? "EMAIL:" + normalizedEmail : "NAME:" + normalizedName;
        ImportedStudent student = new ImportedStudent(rowKey, row.rowNumber(), fullName, names.firstName(),
                names.lastName(), normalizedName, email, normalizedEmail, matchKey, profile,
                profileRef == null ? null : profileRef.publicId(), admission, technology, technologicalProfile,
                techProfileRef == null ? null : techProfileRef.publicId(), certificationLevel(profile),
                Map.copyOf(certifications), current, languages, known, List.copyOf(warnings), !errors.isEmpty());
        return new ParseResult(student, errors);
    }

    private CertificationData certification(XlsxCertificationReader.RowData row, String type, String appliesHeader,
            String statusHeader, String examHeader, String applicationHeader, String scoreHeader,
            String attemptHeader, String deadlineHeader, LocalDate admission, CertificationPolicy policy,
            int rowNumber, List<Issue> errors, List<String> warnings) {
        Boolean appliesValue = parseBoolean(value(row, appliesHeader), appliesHeader, rowNumber, errors);
        boolean applies = Boolean.TRUE.equals(appliesValue);
        String certificationStatus = statusHeader == null ? null : normalizeStatus(value(row, statusHeader));
        String examStatus = examHeader == null ? null : normalizeStatus(value(row, examHeader));
        LocalDate application = applicationHeader == null ? null
                : parseDate(value(row, applicationHeader), applicationHeader, rowNumber, errors, false);
        BigDecimal score = scoreHeader == null ? null
                : parseScore(value(row, scoreHeader), scoreHeader, rowNumber, errors);
        Integer attempt = attemptHeader == null ? null
                : parseAttempt(value(row, attemptHeader), attemptHeader, rowNumber, errors);
        LocalDate calculated = calculatedDeadline(type, admission, policy);
        LocalDate importedDeadline = deadlineHeader == null ? null
                : parseDate(value(row, deadlineHeader), deadlineHeader, rowNumber, errors, false);
        LocalDate deadline = resolveImportDeadline(importedDeadline, calculated);

        if (!hasMeaningfulImportStatus(certificationStatus)) certificationStatus = null;
        if (!hasMeaningfulImportStatus(examStatus)) examStatus = null;

        boolean hasTrackingData = hasImportTrackingData(
                certificationStatus, examStatus, application, score, attempt);
        Boolean approved = approved(certificationStatus, examStatus);
        if (requiresImportApplicationDate(type, applies, approved, certificationStatus)
                && application == null) {
            String requiredHeader = applicationHeader == null ? "FECHA DE APLICACIÓN" : applicationHeader;
            errors.add(new Issue(rowNumber, "STUDENT_IMPORT_CERTIFICATION_APPLICATION_DATE_REQUIRED",
                    typeLabel(type) + ": " + requiredHeader
                            + " es obligatoria cuando el estado indica aprobación o vigencia."));
        }
        if (applies && (certificationStatus != null || examStatus != null || score != null)
                && (attempt == null || attempt == 0) && !oneAgile(type)) {
            if (attemptHeader != null) {
                warnings.add(typeLabel(type)
                        + ": existe resultado sin intento válido; se propone el intento 1 al aplicar.");
            }
            attempt = 1;
        }
        if (attempt != null && attempt == 0) attempt = null;
        if (!applies && hasTrackingData) {
            warnings.add(typeLabel(type)
                    + ": está marcada como No aplica, pero contiene información de seguimiento.");
        }
        if (!applies) {
            certificationStatus = null;
            examStatus = null;
            application = null;
            score = null;
            attempt = null;
            approved = null;
        }

        LocalDate expiration = Boolean.TRUE.equals(approved) && application != null && !oneAgile(type)
                ? CertificationLifecycleCalculator.expiration(application,
                        policy == null || policy.validityYears() == null ? 2 : policy.validityYears())
                : null;
        String validity = validityStatus(expiration, approved);
        String tracking = trackingStatus(applies, certificationStatus, examStatus, approved);
        String comparableStatus = certificationStatusLabel(applies, tracking, validity, approved);
        return new CertificationData(type, applies, comparableStatus, examStatus, internalExamStatus(examStatus),
                application, score, attempt, deadline, expiration, approved, validity, tracking);
    }
    private List<FieldChange> compare(ExistingStudent current, ImportedStudent imported,
            Map<String, CertificationData> currentCerts, ExperienceSnapshot currentExperience) {
        List<FieldChange> changes = new ArrayList<>();
        addChange(changes, "name", "Nombre", current.displayName(), imported.fullName());
        addChange(changes, "profile", "Perfil", current.profileName(), imported.profileName());
        addChange(changes, "admissionDate", "Fecha de alta", text(current.admissionDate()), text(imported.admissionDate()));
        addChange(changes, "primaryTechnology", "Tecnología principal", current.primaryTechnology(), imported.primaryTechnology());
        addChange(changes, "technologicalProfile", "Perfil tecnológico", current.technologicalProfileName(), imported.technologicalProfileName());
        addBooleanChange(changes, "applies:TECHNOLOGICAL", "Aplica Tecnológica", current.appliesTechnological(), imported.flag("TECHNOLOGICAL"));
        addBooleanChange(changes, "applies:DEVELOPMENT_SECURITY", "Aplica Desarrollo Seguro", current.appliesDevelopment(), imported.flag("DEVELOPMENT_SECURITY"));
        addBooleanChange(changes, "applies:NORMATIVE_TESTING", "Aplica Normativa y Testing", current.appliesNormative(), imported.flag("NORMATIVE_TESTING"));
        addBooleanChange(changes, "applies:ONE", "Aplica ONE", current.appliesOne(), imported.flag("ONE"));
        addBooleanChange(changes, "applies:AGILE", "Aplica Agile", current.appliesAgile(), imported.flag("AGILE"));
        for (String type : CERT_TYPES) {
            compareCertification(changes, type, currentCerts.get(type), imported.certifications().get(type));
        }
        addChange(changes, "experience:current", "Tecnología actual y expertise",
                ExperienceSnapshot.display(currentExperience.current()),
                ExperienceSnapshot.display(imported.currentTechnologies()));
        addChange(changes, "experience:languages", "Lenguajes",
                ExperienceSnapshot.display(currentExperience.languages()),
                ExperienceSnapshot.display(imported.languages()));
        addChange(changes, "experience:known", "Tecnologías conocidas",
                ExperienceSnapshot.display(currentExperience.known()),
                ExperienceSnapshot.display(imported.knownTechnologies()));
        return List.copyOf(changes);
    }

    private void compareCertification(List<FieldChange> changes, String type, CertificationData current,
            CertificationData imported) {
        CertificationData before = current == null ? emptyCertification(type) : current;
        CertificationData after = imported == null ? emptyCertification(type) : imported;
        String prefix = "cert:" + type + ":";
        String label = typeLabel(type);
        addChange(changes, prefix + "status", label + " — Estado de certificación",
                text(before.certificationStatus()), text(after.certificationStatus()));
        if (!oneAgile(type)) {
            addChange(changes, prefix + "examStatus", label + " — Estado del examen",
                    text(before.examStatus()), text(after.examStatus()));
            addChange(changes, prefix + "applicationDate", label + " — Fecha de aplicación",
                    text(before.applicationDate()), text(after.applicationDate()));
            addDecimalChange(changes, prefix + "score", label + " — Promedio",
                    before.score10(), after.score10());
            addChange(changes, prefix + "attempt", label + " — Intento",
                    text(before.attempt()), text(after.attempt()));
        }
        if (!oneAgile(type)) {
            addChange(changes, prefix + "deadline", label + " — Fecha límite",
                    text(before.deadline()), text(after.deadline()));
            addChange(changes, prefix + "lifecycle", label + " — Vigencia calculada",
                    lifecycleSummary(before), lifecycleSummary(after));
        }
    }

    private CertificationData emptyCertification(String type) {
        return new CertificationData(type, false, null, null, "NOT_SCHEDULED", null, null, null, null, null,
                null, "NOT_OBTAINED", "PENDING");
    }

    private List<ExistingStudent> existingStudents(Long organizationId) {
        return jdbc.query("""
            SELECT s.STUDENT_ID, s.PUBLIC_ID, s.STUDENT_CODE, s.EMAIL, s.NORMALIZED_EMAIL,
                   s.FIRST_NAME, s.LAST_NAME, s.DISPLAY_NAME, s.STATUS,
                   s.ACCESS_VALID_FROM, s.ACCESS_EXPIRES_ON, s.ADMISSION_DATE,
                   (SELECT MAX(t.TECHNOLOGY_NAME) KEEP (DENSE_RANK FIRST ORDER BY c.IS_PRIMARY DESC,
                                c.ACTIVE DESC, c.UPDATED_AT DESC, c.STUDENT_CERTIFICATION_CYCLE_ID DESC)
                      FROM STUDENT_CERTIFICATION_CYCLE c
                      JOIN QUESTION_TECHNOLOGY t ON t.TECHNOLOGY_ID = c.TECHNOLOGY_ID
                     WHERE c.STUDENT_ID = s.STUDENT_ID
                       AND c.ORGANIZATION_ID = s.ORGANIZATION_ID
                       AND c.CERTIFICATION_TYPE = 'TECHNOLOGICAL') PRIMARY_CERT_TECH_NAME,
                   s.PROFESSIONAL_PROFILE_ID, p.PUBLIC_ID PROFILE_PUBLIC_ID, p.PROFILE_NAME,
                   s.TECHNOLOGICAL_PROFILE_ID, tp.PUBLIC_ID TECH_PROFILE_PUBLIC_ID,
                   tp.PROFILE_NAME TECH_PROFILE_NAME,
                   s.APPLIES_TECH_CERT, s.APPLIES_DEV_SECURITY, s.APPLIES_NORMATIVE_TESTING,
                   s.APPLIES_ONE, s.APPLIES_AGILE, s.VERSION_NO, s.ORGANIZATION_ID
              FROM STUDENT s
              LEFT JOIN CERTIFICATION_PROFILE_CATALOG p ON p.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
              LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tp ON tp.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
             WHERE s.ORGANIZATION_ID = :organizationId AND s.STATUS <> 'DELETED'
            """, Map.of("organizationId", organizationId), this::mapExisting);
    }

    private ExistingStudent mapExisting(ResultSet rs, int rowNum) throws SQLException {
        return new ExistingStudent(rs.getLong("STUDENT_ID"), rs.getLong("ORGANIZATION_ID"),
                rs.getString("PUBLIC_ID"), rs.getString("STUDENT_CODE"), rs.getString("EMAIL"),
                rs.getString("NORMALIZED_EMAIL"), rs.getString("FIRST_NAME"), rs.getString("LAST_NAME"),
                rs.getString("DISPLAY_NAME"), StudentExperienceService.normalizeKey(rs.getString("DISPLAY_NAME")),
                rs.getString("STATUS"), localDate(rs, "ACCESS_VALID_FROM"), localDate(rs, "ACCESS_EXPIRES_ON"),
                localDate(rs, "ADMISSION_DATE"), rs.getString("PRIMARY_CERT_TECH_NAME"),
                rs.getString("PROFILE_PUBLIC_ID"), rs.getString("PROFILE_NAME"),
                rs.getString("TECH_PROFILE_PUBLIC_ID"), rs.getString("TECH_PROFILE_NAME"),
                rs.getBoolean("APPLIES_TECH_CERT"), rs.getBoolean("APPLIES_DEV_SECURITY"),
                rs.getBoolean("APPLIES_NORMATIVE_TESTING"), rs.getBoolean("APPLIES_ONE"),
                rs.getBoolean("APPLIES_AGILE"), rs.getLong("VERSION_NO"));
    }

    private Map<Long, Map<String, CertificationData>> existingCertifications(Long organizationId) {
        Map<Long, Map<String, CertificationData>> result = new HashMap<>();
        jdbc.query("""
            WITH ranked_cycles AS (
                SELECT c.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY c.STUDENT_ID, c.CERTIFICATION_TYPE
                           ORDER BY c.ACTIVE DESC, c.IS_PRIMARY DESC, c.UPDATED_AT DESC,
                                    c.STUDENT_CERTIFICATION_CYCLE_ID DESC) RN
                  FROM STUDENT_CERTIFICATION_CYCLE c
                 WHERE c.ORGANIZATION_ID = :organizationId
            ), ranked_attempts AS (
                SELECT a.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY a.STUDENT_CERTIFICATION_CYCLE_ID
                           ORDER BY a.ATTEMPT_NUMBER DESC,
                                    a.STUDENT_CERTIFICATION_CYCLE_ATTEMPT_ID DESC) RN
                  FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT a
                 WHERE a.ORGANIZATION_ID = :organizationId
            )
            SELECT c.STUDENT_ID, c.CERTIFICATION_TYPE,
                   CASE c.CERTIFICATION_TYPE
                       WHEN 'TECHNOLOGICAL' THEN s.APPLIES_TECH_CERT
                       WHEN 'DEVELOPMENT_SECURITY' THEN s.APPLIES_DEV_SECURITY
                       WHEN 'NORMATIVE_TESTING' THEN s.APPLIES_NORMATIVE_TESTING
                       WHEN 'ONE' THEN s.APPLIES_ONE
                       WHEN 'AGILE' THEN s.APPLIES_AGILE
                   END APPLIES,
                   c.TRACKING_STATUS, c.DEADLINE_DATE,
                   NVL(a.APPLICATION_DATE, c.APPLICATION_DATE) APPLICATION_DATE,
                   c.EXPIRATION_DATE, c.APPROVED, c.VALIDITY_STATUS,
                   a.EXAM_STATUS, a.SCORE, a.ATTEMPT_NUMBER
              FROM ranked_cycles c
              JOIN STUDENT s ON s.STUDENT_ID = c.STUDENT_ID
              LEFT JOIN ranked_attempts a
                ON a.STUDENT_CERTIFICATION_CYCLE_ID = c.STUDENT_CERTIFICATION_CYCLE_ID
               AND a.RN = 1
             WHERE c.RN = 1
            """, Map.of("organizationId", organizationId), rs -> {
                String type = rs.getString("CERTIFICATION_TYPE");
                boolean applies = rs.getBoolean("APPLIES");
                String tracking = rs.getString("TRACKING_STATUS");
                String exam = rs.getString("EXAM_STATUS");
                Boolean approved = nullableBoolean(rs, "APPROVED");
                CertificationData data = new CertificationData(type, applies,
                        certificationStatusLabel(applies, tracking, rs.getString("VALIDITY_STATUS"), approved),
                        examStatusLabel(exam), exam,
                        localDate(rs, "APPLICATION_DATE"), rs.getBigDecimal("SCORE"),
                        nullableInteger(rs, "ATTEMPT_NUMBER"), localDate(rs, "DEADLINE_DATE"),
                        localDate(rs, "EXPIRATION_DATE"), approved, rs.getString("VALIDITY_STATUS"), tracking);
                result.computeIfAbsent(rs.getLong("STUDENT_ID"), ignored -> new HashMap<>())
                        .put(type, data);
            });
        result.replaceAll((key, value) -> Map.copyOf(value));
        return result;
    }

    private static String certificationStatusLabel(boolean applies, String tracking, String validity,
            Boolean approved) {
        if (!applies) return "No aplica";
        if ("EXPIRED".equals(validity)) return "Vencida";
        if ("EXPIRING_SOON".equals(validity)) return "Vigente — Próxima a vencer";
        if ("VALID".equals(validity)) return "Vigente — Regular";
        if (Boolean.TRUE.equals(approved) || "APPROVED".equals(tracking)) return "Aprobada";
        if (Boolean.FALSE.equals(approved) || "NOT_APPROVED".equals(tracking)) return "No aprobada";
        return switch (tracking == null ? "" : tracking) {
            case "SCHEDULED" -> "Programada";
            case "IN_PROGRESS" -> "En proceso";
            case "APPLIED" -> "Presentada";
            case "CANCELLED" -> "Cancelada";
            case "EXPIRED" -> "Vencida";
            default -> "Sin presentar";
        };
    }

    private static String examStatusLabel(String exam) {
        if (exam == null) return null;
        return switch (exam) {
            case "PASSED" -> "Aprobado";
            case "FAILED" -> "No aprobado";
            case "SCHEDULED" -> "Programado";
            case "RESCHEDULED" -> "Reprogramado";
            case "COMPLETED" -> "Presentado";
            case "ABSENT" -> "Ausente";
            case "CANCELLED" -> "Cancelado";
            case "NOT_SCHEDULED" -> "Sin presentar";
            default -> exam.replace('_', ' ').toLowerCase(Locale.ROOT);
        };
    }

    private Map<Long, ExperienceSnapshot> existingExperience(Long organizationId) {
        Map<Long, List<StudentExperienceService.ImportedItem>> current = new HashMap<>();
        Map<Long, List<StudentExperienceService.ImportedItem>> languages = new HashMap<>();
        Map<Long, List<StudentExperienceService.ImportedItem>> known = new HashMap<>();
        jdbc.query("""
            SELECT STUDENT_ID, ITEM_TYPE, ITEM_NAME, LEVEL_CODE
              FROM STUDENT_EXPERIENCE_ITEM
             WHERE ORGANIZATION_ID = :organizationId
             ORDER BY DISPLAY_ORDER
            """, Map.of("organizationId", organizationId), rs -> {
                Map<Long, List<StudentExperienceService.ImportedItem>> target = switch (rs.getString("ITEM_TYPE")) {
                    case "CURRENT_TECHNOLOGY" -> current;
                    case "LANGUAGE" -> languages;
                    default -> known;
                };
                target.computeIfAbsent(rs.getLong("STUDENT_ID"), ignored -> new ArrayList<>())
                        .add(new StudentExperienceService.ImportedItem(rs.getString("ITEM_NAME"), rs.getString("LEVEL_CODE")));
            });
        Set<Long> ids = new HashSet<>(); ids.addAll(current.keySet()); ids.addAll(languages.keySet()); ids.addAll(known.keySet());
        Map<Long, ExperienceSnapshot> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new ExperienceSnapshot(current.getOrDefault(id, List.of()),
                languages.getOrDefault(id, List.of()), known.getOrDefault(id, List.of()))));
        return result;
    }

    private Catalogs catalogs(TenantContext tenant) {
        Long organizationId = tenant.organizationId();
        List<CatalogRef> profiles = organizationCatalog("CERTIFICATION_PROFILE_CATALOG",
                "PROFILE_CODE", "PROFILE_NAME", organizationId);
        List<CatalogRef> technologicalProfiles = organizationCatalog("TECHNOLOGICAL_PROFILE_CATALOG",
                "PROFILE_CODE", "PROFILE_NAME", organizationId);
        Map<String, CertificationPolicy> policies = new HashMap<>();
        jdbc.query("""
            SELECT CERTIFICATION_TYPE, DEADLINE_MONTHS, DEADLINE_DAYS, VALIDITY_YEARS
              FROM ORGANIZATION_CERTIFICATION_POLICY
             WHERE ORGANIZATION_ID = :organizationId AND STATUS = 'ACTIVE'
            """, Map.of("organizationId", organizationId), rs -> {
                policies.put(
                        rs.getString("CERTIFICATION_TYPE"),
                        new CertificationPolicy(nullableInteger(rs, "DEADLINE_MONTHS"),
                                nullableInteger(rs, "DEADLINE_DAYS"), nullableInteger(rs, "VALIDITY_YEARS")));
            });
        return new Catalogs(profiles, technologicalProfiles, Map.copyOf(policies));
    }

    private List<CatalogRef> organizationCatalog(String table, String codeColumn, String nameColumn,
            Long organizationId) {
        return jdbc.query("SELECT PUBLIC_ID, " + codeColumn + " CODE, " + nameColumn + " NAME FROM " + table
                + " WHERE STATUS = 'ACTIVE' AND CONTENT_SCOPE = 'ORGANIZATION'"
                + " AND OWNER_ORGANIZATION_ID = :organizationId ORDER BY " + nameColumn,
                Map.of("organizationId", organizationId),
                (rs, rowNum) -> new CatalogRef(rs.getString("PUBLIC_ID"), rs.getString("CODE"), rs.getString("NAME")));
    }

    private Organization organization(Long organizationId) {
        List<Organization> rows = jdbc.query("""
            SELECT ORGANIZATION_ID, PUBLIC_ID, ORGANIZATION_NAME, ORGANIZATION_CODE,
                   EXPIRES_ON, APPLIES_CERTIFICATIONS
              FROM ORGANIZATION
             WHERE ORGANIZATION_ID = :organizationId AND ORGANIZATION_TYPE = 'CUSTOMER' AND STATUS = 'ACTIVE'
            """, Map.of("organizationId", organizationId), (rs, rowNum) -> new Organization(
                rs.getLong("ORGANIZATION_ID"), rs.getString("PUBLIC_ID"), rs.getString("ORGANIZATION_NAME"),
                rs.getString("ORGANIZATION_CODE"), localDate(rs, "EXPIRES_ON"),
                rs.getBoolean("APPLIES_CERTIFICATIONS")));
        if (rows.isEmpty()) throw new BusinessException("ORGANIZATION_INACTIVE", "La organización actual no está disponible.");
        return rows.getFirst();
    }

    private Organization organizationByPublicId(String publicId) {
        List<Organization> rows = jdbc.query("""
            SELECT ORGANIZATION_ID, PUBLIC_ID, ORGANIZATION_NAME, ORGANIZATION_CODE,
                   EXPIRES_ON, APPLIES_CERTIFICATIONS
              FROM ORGANIZATION
             WHERE PUBLIC_ID = :publicId AND ORGANIZATION_TYPE = 'CUSTOMER' AND STATUS = 'ACTIVE'
            """, Map.of("publicId", publicId), (rs, rowNum) -> new Organization(
                rs.getLong("ORGANIZATION_ID"), rs.getString("PUBLIC_ID"), rs.getString("ORGANIZATION_NAME"),
                rs.getString("ORGANIZATION_CODE"), localDate(rs, "EXPIRES_ON"),
                rs.getBoolean("APPLIES_CERTIFICATIONS")));
        if (rows.isEmpty()) {
            throw new BusinessException("ORGANIZATION_NOT_FOUND",
                    "La organización seleccionada no existe o no está activa.");
        }
        Organization organization = rows.getFirst();
        if (organization.expiresOn() != null && organization.expiresOn().isBefore(LocalDate.now(clock))) {
            throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL",
                    "La organización seleccionada está vencida y no puede recibir colaboradores.");
        }
        return organization;
    }

    private Long studentId(String publicId, Long organizationId) {
        List<Long> ids = jdbc.query("SELECT STUDENT_ID FROM STUDENT WHERE PUBLIC_ID = :publicId AND ORGANIZATION_ID = :organizationId",
                Map.of("publicId", publicId, "organizationId", organizationId), (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El colaborador recién creado no pudo localizarse.");
        return ids.getFirst();
    }

    static AuthenticatedUser certificationImportActor(AuthenticatedUser actor, TenantContext tenant) {
        if (actor == null || actor.roles() == null || !actor.roles().contains(GLOBAL_ADMINISTRATOR_ROLE)) {
            return actor;
        }
        if (tenant == null || !tenant.hasOrganization() || !tenant.globalAdministrator()) {
            throw new BusinessException("STUDENT_IMPORT_FORBIDDEN",
                    "El Administrador global debe seleccionar una organización autorizada para importar certificaciones.");
        }
        Set<String> operationalRoles = new LinkedHashSet<>(actor.roles());
        operationalRoles.add("MANAGER");
        return new AuthenticatedUser(actor.internalId(), actor.publicId(), actor.email(), actor.firstName(),
                actor.lastName(), actor.displayName(), Set.copyOf(operationalRoles), actor.permissions(),
                actor.lastLoginAt(), actor.accessStatus(), actor.accessStartsAt(), actor.accessExpiresAt(),
                actor.passwordChangeRequired(), actor.passwordChangedAt(), actor.temporaryPasswordExpiresAt());
    }

    private TenantContext resolveImportTenant(TenantContext tenant, AuthenticatedUser actor,
            String organizationPublicId) {
        if (tenant == null || actor == null || actor.roles() == null) {
            throw new BusinessException("STUDENT_IMPORT_FORBIDDEN",
                    "No tienes permisos para importar colaboradores.");
        }
        if (actor.roles().contains(GLOBAL_ADMINISTRATOR_ROLE) && tenant.globalAdministrator()) {
            if (organizationPublicId == null || organizationPublicId.isBlank()) {
                throw new BusinessException("STUDENT_IMPORT_ORGANIZATION_REQUIRED",
                        "Selecciona la organización a la que se cargarán los colaboradores.");
            }
            Organization organization = organizationByPublicId(organizationPublicId.trim());
            return TenantContext.organization(organization.id(), organization.publicId(), organization.code(), true);
        }
        boolean organizationOperator = actor.roles().stream().anyMatch(ORGANIZATION_OPERATOR_ROLES::contains);
        if (!organizationOperator || tenant.globalScope() || !tenant.hasOrganization()
                || tenant.globalAdministrator()) {
            throw new BusinessException("STUDENT_IMPORT_FORBIDDEN",
                    "Solo el Administrador global, Gestores y Supervisores autorizados pueden importar colaboradores.");
        }
        if (organizationPublicId != null && !organizationPublicId.isBlank()
                && !organizationPublicId.trim().equals(tenant.organizationPublicId())) {
            throw new BusinessException("STUDENT_IMPORT_ORGANIZATION_FORBIDDEN",
                    "La organización se obtiene de tu sesión y no puede modificarse durante la carga.");
        }
        return tenant;
    }

    private TenantContext effectiveTenantForState(TenantContext tenant, AuthenticatedUser actor,
            PendingImport state) {
        if (state == null || actor == null || !Objects.equals(state.actorId(), actor.internalId())) {
            throw new BusinessException("STUDENT_IMPORT_FORBIDDEN",
                    "La vista previa pertenece a otro usuario.");
        }
        if (actor.roles() != null && actor.roles().contains(GLOBAL_ADMINISTRATOR_ROLE)
                && tenant != null && tenant.globalAdministrator()) {
            return TenantContext.organization(state.organization().id(), state.organization().publicId(),
                    state.organization().code(), true);
        }
        TenantContext effective = resolveImportTenant(tenant, actor, null);
        if (!Objects.equals(state.organizationId(), effective.organizationId())) {
            throw new BusinessException("STUDENT_IMPORT_FORBIDDEN",
                    "La vista previa pertenece a otra organización.");
        }
        return effective;
    }

    private void cleanupExpired() {
        java.time.Instant now = java.time.Instant.now(clock);
        pending.entrySet().removeIf(entry -> !applying.contains(entry.getKey())
                && entry.getValue().expiresAt().isBefore(now));
    }

    private Map<String, List<ExistingStudent>> groupExisting(List<ExistingStudent> existing, boolean email) {
        Map<String, List<ExistingStudent>> result = new HashMap<>();
        for (ExistingStudent item : existing) {
            String key = email ? item.normalizedEmail() : item.normalizedName();
            if (key != null && !key.isBlank()) result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private Map<String, NewSelection> indexNew(List<NewSelection> selections) {
        Map<String, NewSelection> result = new HashMap<>();
        if (selections != null) selections.forEach(value -> { if (value != null && value.rowKey() != null) result.put(value.rowKey(), value); });
        return result;
    }
    private Map<String, ChangeSelection> indexChanges(List<ChangeSelection> selections) {
        Map<String, ChangeSelection> result = new HashMap<>();
        if (selections != null) selections.forEach(value -> { if (value != null && value.studentPublicId() != null) result.put(value.studentPublicId(), value); });
        return result;
    }
    private Map<String, LowSelection> indexLows(List<LowSelection> selections) {
        Map<String, LowSelection> result = new HashMap<>();
        if (selections != null) selections.forEach(value -> { if (value != null && value.studentPublicId() != null) result.put(value.studentPublicId(), value); });
        return result;
    }


    private void validateSelections(PendingImport state, Map<String, NewSelection> newSelections,
            Map<String, ChangeSelection> changeSelections, Map<String, LowSelection> lowSelections) {
        if (!state.newRowKeys().containsAll(newSelections.keySet())
                || !state.allowedChangeFields().keySet().containsAll(changeSelections.keySet())
                || !state.possibleLowPublicIds().containsAll(lowSelections.keySet())) {
            throw new BusinessException("STUDENT_IMPORT_SELECTION_INVALID",
                    "La selección contiene colaboradores que no pertenecen a la vista previa autorizada.");
        }
        for (ChangeSelection selection : changeSelections.values()) {
            Set<String> allowed = state.allowedChangeFields().getOrDefault(selection.studentPublicId(), Set.of());
            if (!allowed.containsAll(selection.fields())) {
                throw new BusinessException("STUDENT_IMPORT_FIELDS_INVALID",
                        "La selección contiene campos que no fueron mostrados en la comparación.");
            }
        }
        Set<String> validLowActions = Set.of("KEEP", "IGNORE", "DEACTIVATE");
        if (lowSelections.values().stream().anyMatch(value -> value == null
                || value.action() == null || !validLowActions.contains(value.action()))) {
            throw new BusinessException("STUDENT_IMPORT_LOW_ACTION_INVALID",
                    "Selecciona una acción válida para cada posible baja.");
        }
    }

    private String requireEmail(String value, int row) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("STUDENT_IMPORT_EMAIL_REQUIRED", "Captura el correo del nuevo colaborador de la fila " + row + ".");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches() || normalized.length() > 254) {
            throw new BusinessException("STUDENT_IMPORT_EMAIL_INVALID", "El correo de la fila " + row + " no tiene un formato válido.");
        }
        return normalized;
    }

    private LocalDate accessExpiry(Organization organization) {
        LocalDate today = LocalDate.now(clock);
        return organization.expiresOn() != null && !organization.expiresOn().isBefore(today)
                ? organization.expiresOn() : today.plusYears(1);
    }

    private String generatedStudentCode(String digest, ImportedStudent imported) {
        String source = digest + ":" + imported.rowNumber() + ":" + imported.normalizedName();
        return "IMP-" + sha256(source.getBytes(StandardCharsets.UTF_8)).substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private ImportedStudent materializeCatalogs(Long organizationId, ImportedStudent imported, Long actorId,
            Set<String> selectedFields) {
        boolean completeRow = selectedFields == null;
        boolean resolveProfile = completeRow || selectedFields.contains("profile");
        boolean resolveTechnologicalProfile = completeRow || selectedFields.contains("technologicalProfile");
        boolean resolveTechnology = completeRow || selectedFields.contains("primaryTechnology")
                || selectedFields.stream().anyMatch(field -> field.startsWith("cert:TECHNOLOGICAL:"));

        CatalogRef profile = resolveProfile
                ? ensureProfessionalProfile(organizationId, imported.profileName(), actorId)
                : imported.profilePublicId() == null ? null
                        : new CatalogRef(imported.profilePublicId(), null, imported.profileName());
        CatalogRef technologicalProfile = resolveTechnologicalProfile
                ? ensureTechnologicalProfile(organizationId, imported.technologicalProfileName(), actorId)
                : imported.technologicalProfilePublicId() == null ? null
                        : new CatalogRef(imported.technologicalProfilePublicId(), null,
                                imported.technologicalProfileName());
        if (resolveTechnology && imported.primaryTechnology() != null) {
            ensureQuestionTechnology(organizationId, imported.primaryTechnology(), actorId);
        }
        return new ImportedStudent(imported.rowKey(), imported.rowNumber(), imported.fullName(),
                imported.firstName(), imported.lastName(), imported.normalizedName(), imported.email(),
                imported.normalizedEmail(), imported.matchKey(), imported.profileName(),
                profile == null ? null : profile.publicId(), imported.admissionDate(),
                imported.primaryTechnology(), imported.technologicalProfileName(),
                technologicalProfile == null ? null : technologicalProfile.publicId(),
                imported.certificationLevel(), imported.certifications(), imported.currentTechnologies(),
                imported.languages(), imported.knownTechnologies(), imported.warnings(),
                imported.hasBlockingErrors());
    }

    private CatalogRef ensureProfessionalProfile(Long organizationId, String name, Long actorId) {
        if (name == null || name.isBlank()) return null;
        String code = catalogCode("PRF", name, organizationId, 120);
        CatalogState existing = resolveCatalogAnyStatus("CERTIFICATION_PROFILE_CATALOG",
                "PROFILE_CODE", "PROFILE_NAME", organizationId, name, code,
                "perfil profesional");
        if (existing != null) return activateCatalogIfNecessary(
                "CERTIFICATION_PROFILE_CATALOG", organizationId, existing, actorId);
        String publicId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                INSERT INTO CERTIFICATION_PROFILE_CATALOG
                    (PUBLIC_ID, PROFILE_CODE, PROFILE_NAME, DESCRIPTION, STATUS,
                     SUGGESTED_TECH_PROFILE, SORT_ORDER, CREATED_BY, UPDATED_BY,
                     CREATED_AT, UPDATED_AT, VERSION_NO, CONTENT_SCOPE, OWNER_ORGANIZATION_ID)
                VALUES (:publicId, :code, :name, :description, 'ACTIVE',
                        NULL, (SELECT NVL(MAX(SORT_ORDER), 0) + 10
                                 FROM CERTIFICATION_PROFILE_CATALOG
                                WHERE CONTENT_SCOPE = 'ORGANIZATION'
                                  AND OWNER_ORGANIZATION_ID = :organizationId),
                        :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0,
                        'ORGANIZATION', :organizationId)
                """, new MapSqlParameterSource()
                    .addValue("publicId", publicId)
                    .addValue("code", code)
                    .addValue("name", name)
                    .addValue("description", "Creado durante la carga confirmada de colaboradores.")
                    .addValue("actorId", actorId)
                    .addValue("organizationId", organizationId));
        } catch (DataIntegrityViolationException exception) {
            CatalogState concurrent = resolveCatalogAnyStatus("CERTIFICATION_PROFILE_CATALOG",
                    "PROFILE_CODE", "PROFILE_NAME", organizationId, name, code,
                    "perfil profesional");
            if (concurrent != null) return activateCatalogIfNecessary(
                    "CERTIFICATION_PROFILE_CATALOG", organizationId, concurrent, actorId);
            throw new BusinessException("STUDENT_IMPORT_PROFILE_CREATE_CONFLICT",
                    "No fue posible crear el perfil '" + name + "' en la organización.");
        }
        return new CatalogRef(publicId, code, name);
    }

    private CatalogRef ensureTechnologicalProfile(Long organizationId, String name, Long actorId) {
        if (name == null || name.isBlank()) return null;
        String code = catalogCode("TPR", name, organizationId, 40);
        CatalogState existing = resolveCatalogAnyStatus("TECHNOLOGICAL_PROFILE_CATALOG",
                "PROFILE_CODE", "PROFILE_NAME", organizationId, name, code,
                "perfil tecnológico");
        if (existing != null) return activateCatalogIfNecessary(
                "TECHNOLOGICAL_PROFILE_CATALOG", organizationId, existing, actorId);
        String publicId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                INSERT INTO TECHNOLOGICAL_PROFILE_CATALOG
                    (PUBLIC_ID, PROFILE_CODE, PROFILE_NAME, DESCRIPTION, STATUS,
                     DISPLAY_ORDER, CREATED_BY, UPDATED_BY, CREATED_AT, UPDATED_AT,
                     VERSION_NO, CONTENT_SCOPE, OWNER_ORGANIZATION_ID)
                VALUES (:publicId, :code, :name, :description, 'ACTIVE',
                        (SELECT NVL(MAX(DISPLAY_ORDER), 0) + 10
                           FROM TECHNOLOGICAL_PROFILE_CATALOG
                          WHERE CONTENT_SCOPE = 'ORGANIZATION'
                            AND OWNER_ORGANIZATION_ID = :organizationId),
                        :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0,
                        'ORGANIZATION', :organizationId)
                """, new MapSqlParameterSource()
                    .addValue("publicId", publicId)
                    .addValue("code", code)
                    .addValue("name", name)
                    .addValue("description", "Creado durante la carga confirmada de colaboradores.")
                    .addValue("actorId", actorId)
                    .addValue("organizationId", organizationId));
        } catch (DataIntegrityViolationException exception) {
            CatalogState concurrent = resolveCatalogAnyStatus("TECHNOLOGICAL_PROFILE_CATALOG",
                    "PROFILE_CODE", "PROFILE_NAME", organizationId, name, code,
                    "perfil tecnológico");
            if (concurrent != null) return activateCatalogIfNecessary(
                    "TECHNOLOGICAL_PROFILE_CATALOG", organizationId, concurrent, actorId);
            throw new BusinessException("STUDENT_IMPORT_TECH_PROFILE_CREATE_CONFLICT",
                    "No fue posible crear el perfil tecnológico '" + name + "' en la organización.");
        }
        return new CatalogRef(publicId, code, name);
    }

    private CatalogRef ensureQuestionTechnology(Long organizationId, String name, Long actorId) {
        if (name == null || name.isBlank()) return null;
        String code = catalogCode("TEC", name, organizationId, 80);
        CatalogState existing = resolveCatalogAnyStatus("QUESTION_TECHNOLOGY",
                "TECHNOLOGY_CODE", "TECHNOLOGY_NAME", organizationId, name, code,
                "tecnología");
        if (existing != null) return activateCatalogIfNecessary(
                "QUESTION_TECHNOLOGY", organizationId, existing, actorId);
        String publicId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                INSERT INTO QUESTION_TECHNOLOGY
                    (PUBLIC_ID, TECHNOLOGY_CODE, TECHNOLOGY_NAME, DESCRIPTION, STATUS,
                     DISPLAY_ORDER, CREATED_BY, UPDATED_BY, CREATED_AT, UPDATED_AT,
                     VERSION_NO, CONTENT_SCOPE, OWNER_ORGANIZATION_ID)
                VALUES (:publicId, :code, :name, :description, 'ACTIVE',
                        (SELECT NVL(MAX(DISPLAY_ORDER), 0) + 10
                           FROM QUESTION_TECHNOLOGY
                          WHERE CONTENT_SCOPE = 'ORGANIZATION'
                            AND OWNER_ORGANIZATION_ID = :organizationId),
                        :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0,
                        'ORGANIZATION', :organizationId)
                """, new MapSqlParameterSource()
                    .addValue("publicId", publicId)
                    .addValue("code", code)
                    .addValue("name", name)
                    .addValue("description", "Creada durante la carga confirmada de colaboradores.")
                    .addValue("actorId", actorId)
                    .addValue("organizationId", organizationId));
        } catch (DataIntegrityViolationException exception) {
            CatalogState concurrent = resolveCatalogAnyStatus("QUESTION_TECHNOLOGY",
                    "TECHNOLOGY_CODE", "TECHNOLOGY_NAME", organizationId, name, code,
                    "tecnología");
            if (concurrent != null) return activateCatalogIfNecessary(
                    "QUESTION_TECHNOLOGY", organizationId, concurrent, actorId);
            throw new BusinessException("STUDENT_IMPORT_TECHNOLOGY_CREATE_CONFLICT",
                    "No fue posible crear la tecnología principal '" + name + "' en la organización.");
        }
        return new CatalogRef(publicId, code, name);
    }

    private CatalogState resolveCatalogAnyStatus(String table, String codeColumn, String nameColumn,
            Long organizationId, String value, String generatedCode, String catalogLabel) {
        List<CatalogState> rows = jdbc.query("SELECT PUBLIC_ID, " + codeColumn + " CODE, "
                        + nameColumn + " NAME, STATUS FROM " + table
                        + " WHERE CONTENT_SCOPE = 'ORGANIZATION'"
                        + " AND OWNER_ORGANIZATION_ID = :organizationId",
                Map.of("organizationId", organizationId),
                (rs, rowNum) -> new CatalogState(rs.getString("PUBLIC_ID"), rs.getString("CODE"),
                        rs.getString("NAME"), rs.getString("STATUS")));
        String normalizedValue = StudentExperienceService.normalizeKey(value);
        List<CatalogState> byName = rows.stream()
                .filter(item -> normalizedValue.equals(StudentExperienceService.normalizeKey(item.name())))
                .toList();
        if (byName.size() == 1) return byName.getFirst();
        if (byName.size() > 1) {
            List<CatalogState> deterministic = byName.stream()
                    .filter(item -> generatedCode.equalsIgnoreCase(item.code()))
                    .toList();
            if (deterministic.size() == 1) return deterministic.getFirst();
            throw new BusinessException("STUDENT_IMPORT_CATALOG_AMBIGUOUS",
                    "Existen varios registros equivalentes para el " + catalogLabel + " '" + value
                            + "' en la organización. Revisa el catálogo antes de continuar.");
        }
        return rows.stream().filter(item -> generatedCode.equalsIgnoreCase(item.code()))
                .findFirst().orElse(null);
    }

    private CatalogRef activateCatalogIfNecessary(String table, Long organizationId,
            CatalogState existing, Long actorId) {
        if (!"ACTIVE".equals(existing.status())) {
            int updated = jdbc.update("UPDATE " + table
                            + " SET STATUS = 'ACTIVE', UPDATED_BY = :actorId,"
                            + " UPDATED_AT = SYSTIMESTAMP, VERSION_NO = VERSION_NO + 1"
                            + " WHERE PUBLIC_ID = :publicId AND CONTENT_SCOPE = 'ORGANIZATION'"
                            + " AND OWNER_ORGANIZATION_ID = :organizationId",
                    new MapSqlParameterSource()
                            .addValue("actorId", actorId)
                            .addValue("publicId", existing.publicId())
                            .addValue("organizationId", organizationId));
            if (updated != 1) {
                throw new BusinessException("STUDENT_IMPORT_CATALOG_ACTIVATION_CONFLICT",
                        "El catálogo cambió durante la importación. Vuelve a analizar el archivo.");
            }
        }
        return new CatalogRef(existing.publicId(), existing.code(), existing.name());
    }

    private String catalogCode(String prefix, String name, Long organizationId, int maxLength) {
        String normalized = StudentExperienceService.normalizeKey(name).replace(' ', '_');
        String suffix = "_" + sha256((organizationId + ":" + normalized)
                .getBytes(StandardCharsets.UTF_8)).substring(0, 10).toUpperCase(Locale.ROOT);
        int available = Math.max(1, maxLength - prefix.length() - suffix.length() - 1);
        String base = normalized.length() > available ? normalized.substring(0, available) : normalized;
        return prefix + "_" + base + suffix;
    }

    private CatalogRef resolveCatalog(List<CatalogRef> catalogs, String value) {
        if (value == null || value.isBlank()) return null;
        String key = StudentExperienceService.normalizeKey(value);
        List<CatalogRef> matches = catalogs.stream().filter(item -> key.equals(StudentExperienceService.normalizeKey(item.name()))
                || key.equals(StudentExperienceService.normalizeKey(item.code()))).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private NameParts splitName(String fullName, int row, List<Issue> errors) {
        if (fullName == null) return new NameParts("", "");
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) {
            errors.add(new Issue(row, "STUDENT_IMPORT_NAME_INCOMPLETE",
                    "El nombre debe incluir nombre y apellidos para crear o actualizar una cuenta."));
            return new NameParts(fullName, "");
        }
        int firstCount = parts.length >= 4 ? 2 : 1;
        String first = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, firstCount));
        String last = String.join(" ", java.util.Arrays.copyOfRange(parts, firstCount, parts.length));
        return new NameParts(first, last);
    }

    private List<StudentExperienceService.ImportedItem> parseExperience(String value) {
        if (value == null || value.isBlank()) return List.of();
        Map<String, StudentExperienceService.ImportedItem> unique = new LinkedHashMap<>();
        for (String token : value.split("[,;|\\n]+")) {
            String item = token.trim();
            if (item.isBlank()) continue;
            String level = null;
            java.util.regex.Matcher matcher = Pattern.compile("(?i)^(.*?)[\\s-]+(JR|STD|SR)$").matcher(item);
            if (matcher.matches()) {
                item = matcher.group(1).trim();
                level = matcher.group(2).toUpperCase(Locale.ROOT);
            }
            if (!item.isBlank()) unique.putIfAbsent(StudentExperienceService.normalizeKey(item),
                    new StudentExperienceService.ImportedItem(item, level));
        }
        return List.copyOf(unique.values());
    }

    static Boolean parseImportBoolean(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = StudentExperienceService.normalizeKey(value);
        if (Set.of("SI", "S", "YES", "1", "APLICA").contains(normalized)
                || normalized.startsWith("SI ")) return true;
        if (Set.of("NO", "N", "0", "NO APLICA", "NA").contains(normalized)
                || normalized.startsWith("NO ")) return false;
        return null;
    }

    private Boolean parseBoolean(String value, String field, int row, List<Issue> errors) {
        Boolean parsed = parseImportBoolean(value);
        if (parsed != null) return parsed;
        errors.add(new Issue(row, "STUDENT_IMPORT_BOOLEAN_INVALID", field + " debe contener Sí, No o No aplica."));
        return false;
    }

    static LocalDate parseImportDateValue(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try {
            BigDecimal serial = new BigDecimal(text.replace(",", "."));
            if (serial.compareTo(BigDecimal.valueOf(1000)) > 0) {
                return LocalDate.of(1899, 12, 30)
                        .plusDays(serial.setScale(0, RoundingMode.DOWN).longValue());
            }
        } catch (NumberFormatException ignored) { }
        List<DateTimeFormatter> formats = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
                DateTimeFormatter.ofPattern("d.M.uuuu"));
        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private LocalDate parseDate(String value, String field, int row, List<Issue> errors, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) errors.add(new Issue(row, "STUDENT_IMPORT_DATE_REQUIRED", field + " es obligatoria."));
            return null;
        }
        LocalDate parsed = parseImportDateValue(value);
        if (parsed != null) return parsed;
        errors.add(new Issue(row, "STUDENT_IMPORT_DATE_INVALID",
                field + " contiene una fecha inválida: " + value.trim() + "."));
        return null;
    }

    private BigDecimal parseScore(String value, String field, int row, List<Issue> errors) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal score = new BigDecimal(value.trim().replace(",", "."));
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
                errors.add(new Issue(row, "STUDENT_IMPORT_SCORE_RANGE", field + " debe estar entre 0 y 10."));
                return null;
            }
            return score.setScale(Math.min(Math.max(score.scale(), 0), 2), RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            errors.add(new Issue(row, "STUDENT_IMPORT_SCORE_INVALID", field + " no contiene un promedio válido."));
            return null;
        }
    }

    private Integer parseAttempt(String value, String field, int row, List<Issue> errors) {
        if (value == null || value.isBlank()) return null;
        try {
            int attempt = new BigDecimal(value.trim().replace(",", ".")).intValueExact();
            if (attempt < 0) throw new ArithmeticException();
            return attempt;
        } catch (ArithmeticException | NumberFormatException exception) {
            errors.add(new Issue(row, "STUDENT_IMPORT_ATTEMPT_INVALID", field + " debe ser 0, 1, 2 o un entero positivo."));
            return null;
        }
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = StudentExperienceService.normalizeKey(value);
        return switch (normalized) {
            case "NO APLICA" -> "No aplica";
            case "SIN PRESENTAR PROXIMO A VENCER", "SIN PRESENTAR PROXIMA A VENCER" -> "Sin presentar — Próxima a vencer";
            case "VIGENTE PROXIMO A VENCER", "VIGENTE PROXIMA A VENCER" -> "Vigente — Próxima a vencer";
            case "VIGENTE REGULAR" -> "Vigente — Regular";
            case "APROBADO", "APROBADA" -> "Aprobada";
            case "NO APROBADO", "NO APROBADA" -> "No aprobada";
            case "PENDIENTE" -> "Pendiente";
            case "SIN PRESENTAR" -> "Sin presentar";
            case "VENCIDO", "VENCIDA" -> "Vencida";
            default -> value.trim().replaceAll("\\s+", " ");
        };
    }

    static boolean requiresImportApplicationDate(String type, boolean applies, Boolean approved,
            String certificationStatus) {
        return applies && !oneAgile(type)
                && (Boolean.TRUE.equals(approved) || statusContains(certificationStatus, "VIGENTE"));
    }
    static boolean hasMeaningfulImportStatus(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = StudentExperienceService.normalizeKey(value);
        return !Set.of("NO APLICA", "NA", "N A").contains(normalized);
    }
    static boolean hasImportTrackingData(String certificationStatus, String examStatus,
            LocalDate application, BigDecimal score, Integer attempt) {
        return hasMeaningfulImportStatus(certificationStatus)
                || hasMeaningfulImportStatus(examStatus)
                || application != null || score != null || (attempt != null && attempt > 0);
    }
    static LocalDate resolveImportDeadline(LocalDate importedDeadline, LocalDate calculatedDeadline) {
        return importedDeadline == null ? calculatedDeadline : importedDeadline;
    }
    private Boolean approved(String certificationStatus, String examStatus) {
        String combined = StudentExperienceService.normalizeKey((certificationStatus == null ? "" : certificationStatus)
                + " " + (examStatus == null ? "" : examStatus));
        if (combined.contains("NO APROB") || combined.contains("FAILED")) return false;
        if (combined.contains("APROB") || combined.contains("VIGENTE") || combined.contains("PASSED")) return true;
        return null;
    }

    private String trackingStatus(boolean applies, String certificationStatus, String examStatus, Boolean approved) {
        if (!applies) return "CANCELLED";
        if (Boolean.TRUE.equals(approved)) return "APPROVED";
        if (Boolean.FALSE.equals(approved)) return "NOT_APPROVED";
        String value = StudentExperienceService.normalizeKey((certificationStatus == null ? "" : certificationStatus)
                + " " + (examStatus == null ? "" : examStatus));
        if (value.contains("VENCID")) return "EXPIRED";
        if (value.contains("PROGRAM") || value.contains("SCHEDULE")) return "SCHEDULED";
        if (value.contains("PRESENT") || value.contains("APLIC")) return "APPLIED";
        return "PENDING";
    }

    private String internalExamStatus(String value) {
        String normalized = StudentExperienceService.normalizeKey(value);
        if (normalized.contains("APROB") || normalized.contains("PASSED")) return "PASSED";
        if (normalized.contains("NO APROB") || normalized.contains("FAILED")) return "FAILED";
        if (normalized.contains("AUSENTE")) return "ABSENT";
        if (normalized.contains("CANCEL")) return "CANCELLED";
        if (normalized.contains("REPROGRAM")) return "RESCHEDULED";
        if (normalized.contains("PROGRAM")) return "SCHEDULED";
        if (normalized.contains("COMPLET") || normalized.contains("PRESENT")) return "COMPLETED";
        return "NOT_SCHEDULED";
    }

    private String validityStatus(LocalDate expiration, Boolean approved) {
        if (!Boolean.TRUE.equals(approved) || expiration == null) return "NOT_OBTAINED";
        LocalDate today = LocalDate.now(clock);
        if (expiration.isBefore(today)) return "EXPIRED";
        if (!expiration.isAfter(today.plusDays(90))) return "EXPIRING_SOON";
        return "VALID";
    }

    private LocalDate calculatedDeadline(String type, LocalDate admission, CertificationPolicy policy) {
        if (admission == null || "ONE".equals(type) || policy == null
                || (policy.deadlineMonths() == null && policy.deadlineDays() == null)) return null;
        return CertificationLifecycleCalculator.deadline(admission,
                policy.deadlineMonths(), policy.deadlineDays());
    }

    private String certificationLevel(String profile) {
        String normalized = StudentExperienceService.normalizeKey(profile);
        for (String level : List.of("JR", "STD", "SR")) {
            if (Pattern.compile("(^| )" + level + "( |$)").matcher(normalized).find()) return level;
        }
        return null;
    }

    private String value(XlsxCertificationReader.RowData row, String header) { return row.value(header); }
    private String firstValue(XlsxCertificationReader.RowData row, String... headers) {
        for (String header : headers) { String value = row.value(header); if (value != null && !value.isBlank()) return value; }
        return null;
    }
    private String valueStarting(XlsxCertificationReader.RowData row, String normalizedPrefix) {
        String prefix = XlsxCertificationReader.normalizeHeader(normalizedPrefix);
        return row.values().entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue).filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
    }

    private String normalizedText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static boolean intersects(Set<String> values, Set<String> target) {
        return values.stream().anyMatch(target::contains);
    }
    private static boolean same(String first, String second) {
        return StudentExperienceService.normalizeKey(first).equals(StudentExperienceService.normalizeKey(second));
    }
    private static void addChange(List<FieldChange> changes, String key, String label, String current, String imported) {
        if (!same(current, imported)) changes.add(new FieldChange(key, label, text(current), text(imported), true));
    }
    private static void addBooleanChange(List<FieldChange> changes, String key, String label, boolean current, boolean imported) {
        if (current != imported) changes.add(new FieldChange(key, label, current ? "Sí" : "No", imported ? "Sí" : "No", true));
    }
    private static void addDecimalChange(List<FieldChange> changes, String key, String label,
            BigDecimal current, BigDecimal imported) {
        boolean equal = current == null ? imported == null : imported != null && current.compareTo(imported) == 0;
        if (!equal) changes.add(new FieldChange(key, label, text(current), text(imported), true));
    }
    private static String lifecycleSummary(CertificationData value) {
        if (value == null || !value.applies()) return "No aplica";
        return text(value.validityStatus()) + (value.expiration() == null ? "" : " · Vence " + value.expiration());
    }
    private static String text(Object value) { return value == null || value.toString().isBlank() ? "Sin información" : value.toString(); }
    private static String nullIfBlank(String value) { return value == null || value.isBlank() ? null : value; }
    private static Integer booleanNumber(Boolean value) { return value == null ? null : value ? 1 : 0; }
    private static java.sql.Date sqlDate(LocalDate value) { return value == null ? null : java.sql.Date.valueOf(value); }
    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column); return value == null ? null : value.toLocalDate();
    }
    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value;
    }
    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value == 1;
    }
    private static boolean statusContains(String value, String expected) {
        return StudentExperienceService.normalizeKey(value).contains(StudentExperienceService.normalizeKey(expected));
    }
    private static boolean oneAgile(String type) { return "ONE".equals(type) || "AGILE".equals(type); }
    private static String typeLabel(String type) {
        return switch (type) {
            case "TECHNOLOGICAL" -> "Certificación tecnológica";
            case "DEVELOPMENT_SECURITY" -> "Desarrollo Seguro";
            case "NORMATIVE_TESTING" -> "Normativa y Testing";
            case "ONE" -> "ONE";
            case "AGILE" -> "Agile";
            default -> type;
        };
    }
    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private record RowOutcome(int created, int updated, Credential credential) {
        static RowOutcome none() { return new RowOutcome(0, 0, null); }
    }
    private record ParseResult(ImportedStudent student, List<Issue> errors) {}
    private record NameParts(String firstName, String lastName) {}
    private record CatalogRef(String publicId, String code, String name) {}
    private record CatalogState(String publicId, String code, String name, String status) {}
    private record Catalogs(List<CatalogRef> profiles, List<CatalogRef> technologicalProfiles,
            Map<String, CertificationPolicy> policies) {
        CertificationPolicy policy(String type) {
            CertificationPolicy configured = policies.get(type);
            if (configured != null) return configured;
            return switch (type) {
                case "TECHNOLOGICAL" -> new CertificationPolicy(1, 0, 2);
                case "DEVELOPMENT_SECURITY" -> new CertificationPolicy(3, 0, 2);
                case "NORMATIVE_TESTING" -> new CertificationPolicy(2, 0, 2);
                case "AGILE" -> new CertificationPolicy(3, 0, null);
                case "ONE" -> new CertificationPolicy(null, null, null);
                default -> null;
            };
        }
    }
    private record CertificationPolicy(Integer deadlineMonths, Integer deadlineDays, Integer validityYears) {}
    private record Organization(Long id, String publicId, String name, String code, LocalDate expiresOn, boolean appliesCertifications) {}
    private record ExistingStudent(Long id, Long organizationId, String publicId, String studentCode, String email,
            String normalizedEmail, String firstName, String lastName, String displayName, String normalizedName,
            String status, LocalDate validFrom, LocalDate expiresAt, LocalDate admissionDate,
            String primaryTechnology, String profilePublicId, String profileName,
            String technologicalProfilePublicId, String technologicalProfileName,
            boolean appliesTechnological, boolean appliesDevelopment, boolean appliesNormative,
            boolean appliesOne, boolean appliesAgile, Long version) {}
    private record ImportedStudent(String rowKey, int rowNumber, String fullName, String firstName, String lastName,
            String normalizedName, String email, String normalizedEmail, String matchKey,
            String profileName, String profilePublicId, LocalDate admissionDate,
            String primaryTechnology, String technologicalProfileName, String technologicalProfilePublicId,
            String certificationLevel, Map<String, CertificationData> certifications,
            List<StudentExperienceService.ImportedItem> currentTechnologies,
            List<StudentExperienceService.ImportedItem> languages,
            List<StudentExperienceService.ImportedItem> knownTechnologies,
            List<String> warnings, boolean hasBlockingErrors) {
        boolean flag(String type) { CertificationData value = certifications.get(type); return value != null && value.applies(); }
    }
    private record CertificationData(String type, boolean applies, String certificationStatus, String examStatus,
            String internalExamStatus, LocalDate applicationDate, BigDecimal score10, Integer attempt,
            LocalDate deadline, LocalDate expiration, Boolean approved, String validityStatus,
            String trackingStatus) {}
    private record Match(ImportedStudent imported, ExistingStudent existing) {}
    private record PendingImport(String token, Long actorId, Long organizationId, String digest,
            java.time.Instant expiresAt, Organization organization, List<ImportedStudent> imported,
            Map<String, Match> matches, Set<String> newRowKeys,
            Map<String, Set<String>> allowedChangeFields, Set<String> possibleLowPublicIds,
            List<Issue> errors, List<Issue> conflicts) {}
    private record ExperienceSnapshot(List<StudentExperienceService.ImportedItem> current,
            List<StudentExperienceService.ImportedItem> languages,
            List<StudentExperienceService.ImportedItem> known) {
        static ExperienceSnapshot empty() { return new ExperienceSnapshot(List.of(), List.of(), List.of()); }
        String normalized() { return normalize(current) + "|" + normalize(languages) + "|" + normalize(known); }
        String summary() {
            List<String> sections = new ArrayList<>();
            if (!current.isEmpty()) sections.add("Actual: " + display(current));
            if (!languages.isEmpty()) sections.add("Lenguajes: " + display(languages));
            if (!known.isEmpty()) sections.add("Conocidas: " + display(known));
            return sections.isEmpty() ? "Sin información" : String.join(" · ", sections);
        }
        private static String normalize(List<StudentExperienceService.ImportedItem> values) {
            return values.stream().map(value -> StudentExperienceService.normalizeKey(value.name()) + ":" + text(value.level()))
                    .sorted().collect(java.util.stream.Collectors.joining(","));
        }
        private static String display(List<StudentExperienceService.ImportedItem> values) {
            return values.stream().map(value -> value.name() + (value.level() == null ? "" : " — " + value.level()))
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }

    public record Issue(int row, String code, String message) {}
    public record FieldChange(String key, String field, String currentValue, String excelValue, boolean selected) {}
    public record NewStudentPreview(String rowKey, int row, String collaborator, String profile,
            String primaryTechnology, String suggestedEmail, List<String> warnings) {}
    public record ChangedStudentPreview(String studentPublicId, String rowKey, String collaborator,
            List<FieldChange> changes, List<String> warnings) {}
    public record PossibleLowPreview(String studentPublicId, String collaborator, String email, String action) {}
    public record Preview(String token, String fileName, String sheetName, String organizationName,
            String organizationCode, int totalRows, List<NewStudentPreview> newStudents,
            List<ChangedStudentPreview> changedStudents, List<PossibleLowPreview> possibleLows,
            List<Issue> conflicts, List<Issue> errors, String notice) {}
    public record NewSelection(String rowKey, String email, boolean selected) {}
    public record ChangeSelection(String studentPublicId, Set<String> fields) {
        public ChangeSelection { fields = fields == null ? Set.of() : Set.copyOf(fields); }
    }
    public record LowSelection(String studentPublicId, String action) {}
    public record ApplyCommand(String token, List<NewSelection> newStudents,
            List<ChangeSelection> changedStudents, List<LowSelection> possibleLows) {}
    public record Credential(String organization, String organizationCode, String collaborator,
            String email, String temporaryPassword) {}
    public record ApplyResult(int created, int updated, int possibleLowsProcessed,
            List<Issue> errors, List<Credential> credentials, String credentialsNotice) {}
}
