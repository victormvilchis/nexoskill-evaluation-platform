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
    private static final String REUSABLE_CERTIFICATION_CONFLICT =
            "STUDENT_IMPORT_CERTIFICATION_VALIDITY_CONFLICT";
    private static final List<String> CERT_TYPES = List.of(
            "TECHNOLOGICAL", "DEVELOPMENT_SECURITY", "NORMATIVE_TESTING", "ONE", "AGILE", "JIRA");

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
        Map<ConflictDecisionKey, String> storedConflictDecisions =
                existingConflictDecisions(effectiveTenant.organizationId());
        Map<Long, Map<String, String>> existingImportFingerprints =
                existingImportFingerprints(effectiveTenant.organizationId());

        List<ImportedStudent> parsed = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        for (XlsxCertificationReader.RowData row : sheet.rows()) {
            ParseResult result = parseRow(row, catalogs, organization);
            parsed.add(result.student());
            errors.addAll(result.errors());
            warnings.addAll(result.warnings());
        }

        Map<String, List<ImportedStudent>> fileKeys = new HashMap<>();
        for (ImportedStudent item : parsed) {
            if (item.matchKey() != null && !item.matchKey().isBlank()) {
                fileKeys.computeIfAbsent(item.matchKey(), ignored -> new ArrayList<>()).add(item);
            }
        }
        Set<String> duplicateRowKeys = new HashSet<>();
        List<ConflictPreview> conflicts = new ArrayList<>();
        Map<String, String> conflictFingerprints = new HashMap<>();
        Map<String, String> automaticConflictResolutions = new HashMap<>();
        fileKeys.values().stream().filter(values -> values.size() > 1).forEach(values -> values.forEach(value -> {
            duplicateRowKeys.add(value.rowKey());
            conflicts.add(omissionConflict(value, "DUPLICATE_FILE_IDENTITY", "Identidad repetida en el archivo",
                    "El archivo contiene más de una fila para la misma identidad. Omite esta fila y conserva únicamente el registro correcto."));
        }));

        Map<String, List<ExistingStudent>> byEmail = groupExisting(existing, true);
        Map<String, List<ExistingStudent>> byName = groupExisting(existing, false);
        Set<Long> matchedIds = new HashSet<>();
        Set<Long> referencedIds = new HashSet<>();
        List<NewStudentPreview> newStudents = new ArrayList<>();
        List<ChangedStudentPreview> changedStudents = new ArrayList<>();
        Map<String, Match> matches = new HashMap<>();
        Map<String, ImportedStudent> effectiveImportedByRow = new HashMap<>();
        parsed.forEach(value -> effectiveImportedByRow.put(value.rowKey(), value));

        for (ImportedStudent imported : parsed) {
            List<ExistingStudent> candidates = imported.normalizedEmail() == null
                    ? List.of() : byEmail.getOrDefault(imported.normalizedEmail(), List.of());
            if (candidates.isEmpty() && imported.normalizedName() != null && !imported.normalizedName().isBlank()) {
                candidates = byName.getOrDefault(imported.normalizedName(), List.of());
            }
            if (candidates.size() > 1) {
                candidates.forEach(candidate -> referencedIds.add(candidate.id()));
                conflicts.add(omissionConflict(imported, "AMBIGUOUS_IDENTITY", "Identidad ambigua",
                        "Existen varios colaboradores que coinciden con esta fila. Omite el registro para no actualizar una cuenta incorrecta."));
                continue;
            }

            ExistingStudent current = candidates.isEmpty() ? null : candidates.getFirst();
            ImportedStudent identified = current != null && blank(imported.fullName())
                    ? imported.withName(current.displayName()) : imported;
            Map<String, CertificationData> currentCertifications = current == null
                    ? Map.of() : existingCertifications.getOrDefault(current.id(), Map.of());
            Reconciliation reconciliation = reconcileCertificationLifecycle(identified, currentCertifications);
            ImportedStudent effectiveImported = reconciliation.student();
            effectiveImportedByRow.put(effectiveImported.rowKey(), effectiveImported);
            for (ConflictPreview conflict : reconciliation.conflicts()) {
                String fingerprint = conflictFingerprint(effectiveImported, conflict);
                ConflictPreview effectiveConflict = conflict;
                if (fingerprint != null) {
                    conflictFingerprints.put(conflict.id(), fingerprint);
                    String previousAction = current == null ? null : storedConflictDecisions.get(
                            new ConflictDecisionKey(current.id(), conflict.certificationType(),
                                    conflict.code(), fingerprint));
                    if (previousAction == null && current != null) {
                        String previousImportFingerprint = existingImportFingerprints
                                .getOrDefault(current.id(), Map.of())
                                .get(conflict.certificationType());
                        previousAction = inferPreviousConflictAction(effectiveImported, conflict,
                                previousImportFingerprint, effectiveTenant.organizationId());
                    }
                    String reusableAction = previousAction;
                    if (reusableAction != null && conflict.actions().stream()
                            .anyMatch(option -> option.value().equals(reusableAction))) {
                        automaticConflictResolutions.put(conflict.id(), reusableAction);
                        effectiveConflict = conflict.withResolution(reusableAction, true);
                    }
                }
                conflicts.add(effectiveConflict);
            }
            warnings.addAll(reconciliation.warnings());

            if (duplicateRowKeys.contains(effectiveImported.rowKey())) {
                candidates.forEach(candidate -> referencedIds.add(candidate.id()));
                continue;
            }
            if (current == null) {
                newStudents.add(new NewStudentPreview(effectiveImported.rowKey(), effectiveImported.rowNumber(),
                        effectiveImported.fullName(), effectiveImported.profileName(),
                        effectiveImported.primaryTechnology(), effectiveImported.email(), effectiveImported.warnings()));
                matches.put(effectiveImported.rowKey(), new Match(effectiveImported, null));
                continue;
            }

            referencedIds.add(current.id());
            if (!matchedIds.add(current.id())) {
                conflicts.add(omissionConflict(effectiveImported, "DUPLICATE_MATCH", "Actualización duplicada",
                        "Más de una fila intenta actualizar al colaborador " + current.displayName()
                                + ". Omite esta fila y conserva únicamente la actualización correcta."));
                continue;
            }
            List<FieldChange> changes = compare(current, effectiveImported, currentCertifications,
                    existingExperience.getOrDefault(current.id(), ExperienceSnapshot.empty()));
            matches.put(effectiveImported.rowKey(), new Match(effectiveImported, current));
            if (!changes.isEmpty()) {
                changedStudents.add(new ChangedStudentPreview(current.publicId(), effectiveImported.rowKey(),
                        current.displayName(), changes, effectiveImported.warnings()));
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
        List<ImportedStudent> effectiveImported = parsed.stream()
                .map(value -> effectiveImportedByRow.getOrDefault(value.rowKey(), value))
                .toList();
        Map<String, ConflictPreview> conflictMap = java.util.Collections.unmodifiableMap(conflicts.stream().collect(
                java.util.stream.Collectors.toMap(ConflictPreview::id, value -> value,
                        (first, second) -> first, LinkedHashMap::new)));
        List<ConflictPreview> uniqueConflicts = List.copyOf(conflictMap.values());
        PendingImport state = new PendingImport(token, actor.internalId(), effectiveTenant.organizationId(), digest,
                java.time.Instant.now(clock).plusSeconds(TOKEN_MINUTES * 60L), organization, effectiveImported,
                Map.copyOf(matches), previewNewRows, previewChangeFields, previewPossibleLows,
                List.copyOf(errors), List.copyOf(warnings), conflictMap,
                Map.copyOf(conflictFingerprints), Map.copyOf(automaticConflictResolutions));
        pending.put(token, state);
        return new Preview(token, fileName, sheet.sheetName(), organization.name(), organization.code(),
                sheet.rows().size(), newStudents, changedStudents, possibleLows, uniqueConflicts, warnings, errors,
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
        Map<String, String> submittedConflictResolutions = indexConflictResolutions(command.conflicts());
        Map<String, String> conflictResolutions = effectiveConflictResolutions(
                state, submittedConflictResolutions);
        if (!applying.add(normalizedToken)) {
            throw new BusinessException("STUDENT_IMPORT_APPLY_IN_PROGRESS",
                    "La importación ya se está aplicando. Espera a que termine antes de volver a confirmar.");
        }
        try {
            validateSelectedNewStudents(effectiveTenant.organizationId(), state, newSelections);
            validateSelections(state, newSelections, changeSelections, lowSelections, conflictResolutions);
            Map<String, ChangeSelection> effectiveChangeSelections = includeConflictFields(
                    state, changeSelections, conflictResolutions);
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
                if (match == null) continue;
                ImportedStudent resolvedImported = resolveConflictDecisions(imported, state, conflictResolutions);
                if (resolvedImported == null) {
                    if (match.existing() != null) {
                        try {
                            rowTransaction.executeWithoutResult(status -> persistConflictDecisions(
                                    state, imported.rowKey(), match.existing().id(),
                                    conflictResolutions));
                        } catch (RuntimeException exception) {
                            LOGGER.error("Student import conflict decision persistence failed for row {} in organization {}",
                                    imported.rowNumber(), effectiveTenant.organizationId(), exception);
                            applyErrors.add(importFailure(imported.rowNumber(),
                                    "No fue posible conservar la decisión de " + imported.fullName(), exception));
                        }
                    }
                    continue;
                }
                try {
                    RowOutcome outcome = rowTransaction.execute(status -> applyRow(effectiveTenant, state, resolvedImported, match,
                            newSelections, effectiveChangeSelections, conflictResolutions, requestActor, actor));
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
            Map<String, String> conflictResolutions, StudentService.Actor requestActor, AuthenticatedUser actor) {
        if (match.existing() == null) {
            NewSelection selection = newSelections.get(imported.rowKey());
            if (selection == null || !selection.selected()) return RowOutcome.none();
            ImportedStudent identified = imported.withName(requireFullName(imported.fullName(), imported.rowNumber()));
            ImportedStudent resolved = materializeCatalogs(tenant.organizationId(), identified, actor.internalId(), null);
            String email = requireEmail(selection.email(), resolved.rowNumber());
            String ownerPublicId = tenant.globalAdministrator() ? state.organization().publicId() : null;
            StudentFoundationService.CreateResult result = foundation.create(tenant,
                    new StudentFoundationService.CreateCommand(ownerPublicId,
                            email, resolved.firstName(), resolved.lastName(), resolved.fullName(), StudentStatus.ACTIVE,
                            LocalDate.now(clock), accessExpiry(state.organization()), resolved.admissionDate(),
                            resolved.profilePublicId(), resolved.technologicalProfilePublicId(),
                            resolved.flag("TECHNOLOGICAL"), resolved.flag("DEVELOPMENT_SECURITY"),
                            resolved.flag("NORMATIVE_TESTING"), resolved.flag("ONE"), resolved.flag("AGILE"), resolved.flag("JIRA")),
                    requestActor);
            Long studentId = studentId(result.student().publicId(), tenant.organizationId());
            synchronizeStudentFoundationForCertification(studentId, tenant.organizationId(),
                    result.student().publicId(), resolved.admissionDate());
            persistPrimaryTechnology(tenant.organizationId(), studentId, resolved.primaryTechnology(), actor.internalId());
            persistImportedDetails(tenant, result.student().publicId(), studentId, resolved, actor);
            persistConflictDecisions(state, imported.rowKey(), studentId,
                    conflictResolutions);
            return new RowOutcome(1, 0, new Credential(state.organization().name(), state.organization().code(),
                    resolved.fullName(), email, result.student().studentCode(), result.temporaryPassword()));
        }
        ChangeSelection selection = changeSelections.get(match.existing().publicId());
        if (selection == null || selection.fields().isEmpty()) return RowOutcome.none();
        ImportedStudent resolved = materializeCatalogs(tenant.organizationId(), imported, actor.internalId(),
                selection.fields());
        updateExisting(tenant, match.existing(), resolved, selection.fields(), requestActor, actor);
        persistConflictDecisions(state, imported.rowKey(), match.existing().id(),
                conflictResolutions);
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
        entityManager.flush();
        int updated = jdbc.update("""
            UPDATE STUDENT
               SET ADMISSION_DATE = :admissionDate
             WHERE STUDENT_ID = :studentId
               AND ORGANIZATION_ID = :organizationId
               AND PUBLIC_ID = :studentPublicId
            """, new MapSqlParameterSource()
                .addValue("admissionDate", admissionDate == null ? null : java.sql.Date.valueOf(admissionDate),
                        java.sql.Types.DATE)
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
        if (!Objects.equals(admissionDate, reloaded.getAdmissionDate())) {
            throw new BusinessException("STUDENT_IMPORT_FOUNDATION_SYNC_FAILED",
                    "La fecha de alta no quedó sincronizada antes de aplicar las certificaciones. No se aplicó la fila.");
        }
    }

    private void validateSelectedNewStudents(Long organizationId, PendingImport state,
            Map<String, NewSelection> selections) {
        Map<String, ImportedStudent> importedByKey = state.imported().stream()
                .collect(java.util.stream.Collectors.toMap(ImportedStudent::rowKey, value -> value, (first, second) -> first));
        Set<String> normalized = new LinkedHashSet<>();
        for (NewSelection selection : selections.values()) {
            if (selection == null || !selection.selected()) continue;
            ImportedStudent imported = importedByKey.get(selection.rowKey());
            int row = imported == null ? 0 : imported.rowNumber();
            requireFullName(imported == null ? null : imported.fullName(), row);
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
                "applies:ONE", "applies:AGILE", "applies:JIRA"));
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
                    selectedFields.contains("applies:JIRA") ? imported.flag("JIRA") : detail.appliesJira(),
                    detail.version()), requestActor);
        }
        Long studentId = current.id();
        boolean certificationChanges = selectedFields.stream().anyMatch(field -> field.startsWith("cert:"));
        if (identity || certificationChanges) {
            synchronizeStudentFoundationForCertification(studentId, current.organizationId(),
                    current.publicId(), effectiveAdmissionDate);
        }
        if (selectedFields.contains("primaryTechnology")) {
            persistPrimaryTechnology(current.organizationId(), studentId, imported.primaryTechnology(), actor.internalId());
        }
        if (selectedFields.stream().anyMatch(field -> field.startsWith("experience:"))) {
            ExperienceSnapshot before = existingExperience(current.organizationId())
                    .getOrDefault(studentId, ExperienceSnapshot.empty());
            experienceService.replaceImported(current.organizationId(), studentId,
                    selectedFields.contains("experience:current") ? imported.currentTechnologies() : before.current(),
                    selectedFields.contains("experience:languages") ? imported.languages() : before.languages(),
                    selectedFields.contains("experience:known") ? imported.knownTechnologies() : before.known(),
                    actor.internalId());
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

    private void persistPrimaryTechnology(Long organizationId, Long studentId, String technologyName, Long actorId) {
        if (organizationId == null || studentId == null || actorId == null
                || technologyName == null || technologyName.isBlank()) {
            return;
        }
        CatalogRef technology = ensureQuestionTechnology(organizationId, technologyName, actorId);
        int updated = jdbc.update("""
            UPDATE STUDENT
               SET TECHNOLOGY_ID = (
                       SELECT t.TECHNOLOGY_ID
                         FROM QUESTION_TECHNOLOGY t
                        WHERE t.PUBLIC_ID = :technologyPublicId
                          AND t.STATUS = 'ACTIVE'
                          AND ((t.CONTENT_SCOPE = 'ORGANIZATION' AND t.OWNER_ORGANIZATION_ID = :organizationId)
                               OR t.CONTENT_SCOPE = 'GLOBAL')
                   ),
                   UPDATED_BY = :actorId,
                   UPDATED_AT = SYSTIMESTAMP,
                   VERSION_NO = VERSION_NO + 1
             WHERE STUDENT_ID = :studentId
               AND ORGANIZATION_ID = :organizationId
            """, new MapSqlParameterSource()
                .addValue("technologyPublicId", technology.publicId())
                .addValue("actorId", actorId)
                .addValue("studentId", studentId)
                .addValue("organizationId", organizationId));
        if (updated != 1) {
            throw new BusinessException("STUDENT_IMPORT_PRIMARY_TECHNOLOGY_UPDATE_FAILED",
                    "No fue posible guardar la tecnología principal del colaborador.");
        }
        entityManager.clear();
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
        String fingerprint = certificationFingerprint(data, technologyPublicId, level);
        certificationService.importSnapshot(tenant, studentPublicId,
                new CertificationModels.ImportSnapshotCommand(type, technologyPublicId, level,
                        type == CertificationType.TECHNOLOGICAL, parseTrackingStatus(data.trackingStatus()),
                        data.deadline(), data.applicationDate(), data.lastApprovedApplicationDate(),
                        data.approved(), data.expiration(), parseValidityStatus(data.validityStatus()),
                        parseExamStatus(data.internalExamStatus()),
                        data.score10(), data.attempt(), data.certificationStatus(), fingerprint),
                certificationImportActor(actor, tenant));
    }

    private void persistConflictDecisions(PendingImport state, String rowKey, Long studentId,
            Map<String, String> resolutions) {
        if (studentId == null) return;
        for (ConflictPreview conflict : state.conflicts().values()) {
            if (!conflict.rowKey().equals(rowKey) || conflict.certificationType() == null) continue;
            String fingerprint = state.conflictFingerprints().get(conflict.id());
            String action = resolutions.get(conflict.id());
            if (fingerprint == null || action == null || action.isBlank()) continue;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("publicId", UUID.randomUUID().toString())
                    .addValue("organizationId", state.organizationId())
                    .addValue("studentId", studentId)
                    .addValue("certificationType", conflict.certificationType())
                    .addValue("conflictCode", conflict.code())
                    .addValue("fingerprint", fingerprint)
                    .addValue("action", action);
            jdbc.update("""
                MERGE INTO STUDENT_IMPORT_CONFLICT_DECISION target
                USING (
                    SELECT :organizationId ORGANIZATION_ID, :studentId STUDENT_ID,
                           :certificationType CERTIFICATION_TYPE, :conflictCode CONFLICT_CODE,
                           :fingerprint CONFLICT_FINGERPRINT
                      FROM DUAL
                ) source
                   ON (target.ORGANIZATION_ID = source.ORGANIZATION_ID
                       AND target.STUDENT_ID = source.STUDENT_ID
                       AND target.CERTIFICATION_TYPE = source.CERTIFICATION_TYPE
                       AND target.CONFLICT_CODE = source.CONFLICT_CODE
                       AND target.CONFLICT_FINGERPRINT = source.CONFLICT_FINGERPRINT)
                 WHEN MATCHED THEN UPDATE SET
                      target.ACTION_CODE = :action
                 WHEN NOT MATCHED THEN INSERT (
                      PUBLIC_ID, ORGANIZATION_ID, STUDENT_ID, CERTIFICATION_TYPE,
                      CONFLICT_CODE, CONFLICT_FINGERPRINT, ACTION_CODE
                 ) VALUES (
                      :publicId, :organizationId, :studentId, :certificationType,
                      :conflictCode, :fingerprint, :action
                 )
                """, params);
        }
    }

    private String certificationFingerprint(CertificationData data, String technologyPublicId, CertificationLevel level) {
        String canonical = String.join("|", data.type(), Boolean.toString(data.applies()),
                text(data.certificationStatus()), text(data.internalExamStatus()), text(data.applicationDate()),
                text(data.lastApprovedApplicationDate()), text(data.score10()), text(data.attempt()),
                text(data.deadline()), text(data.expiration()),
                text(data.approved()), text(data.validityStatus()), text(data.trackingStatus()),
                text(technologyPublicId), text(level));
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus parseValidityStatus(String value) {
        try {
            return value == null
                    ? com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus.NOT_OBTAINED
                    : com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus.NOT_OBTAINED;
        }
    }

    private CertificationData mergeCertification(CertificationData current, CertificationData imported,
            Set<String> selectedFields, String type) {
        if (imported == null) return current;
        if (current == null) return imported;
        String prefix = "cert:" + type + ":";
        return new CertificationData(type,
                imported.applies(),
                selectedFields.contains(prefix + "status") ? imported.certificationStatus() : current.certificationStatus(),
                selectedFields.contains(prefix + "status") ? imported.excelCertificationStatus() : current.excelCertificationStatus(),
                selectedFields.contains(prefix + "examStatus") ? imported.examStatus() : current.examStatus(),
                selectedFields.contains(prefix + "examStatus") ? imported.internalExamStatus() : current.internalExamStatus(),
                selectedFields.contains(prefix + "applicationDate") || selectedFields.contains(prefix + "lifecycle")
                        ? imported.applicationDate() : current.applicationDate(),
                selectedFields.contains(prefix + "lastApprovedApplicationDate")
                        || selectedFields.contains(prefix + "status")
                        || selectedFields.contains(prefix + "applicationDate")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.lastApprovedApplicationDate() : current.lastApprovedApplicationDate(),
                selectedFields.contains(prefix + "score") ? imported.score10() : current.score10(),
                selectedFields.contains(prefix + "failureCount") ? imported.attempt() : current.attempt(),
                selectedFields.contains(prefix + "deadline") ? imported.deadline() : current.deadline(),
                selectedFields.contains(prefix + "deadline") ? imported.explicitDeadline() : current.explicitDeadline(),
                selectedFields.contains(prefix + "applicationDate") || selectedFields.contains(prefix + "status")
                        || selectedFields.contains(prefix + "expiration") || selectedFields.contains(prefix + "lifecycle")
                        ? imported.expiration() : current.expiration(),
                selectedFields.contains(prefix + "status") || selectedFields.contains(prefix + "examStatus")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.approved() : current.approved(),
                selectedFields.contains(prefix + "status") || selectedFields.contains(prefix + "applicationDate")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.validityStatus() : current.validityStatus(),
                selectedFields.contains(prefix + "status") || selectedFields.contains(prefix + "examStatus")
                        || selectedFields.contains(prefix + "lifecycle")
                        ? imported.trackingStatus() : current.trackingStatus(),
                selectedFields.contains(prefix + "processType") || selectedFields.contains(prefix + "status")
                        ? imported.processType() : current.processType(),
                selectedFields.contains(prefix + "referenceDate") || selectedFields.contains(prefix + "applicationDate")
                        ? imported.referenceDate() : current.referenceDate(),
                "IMPORT");
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
        List<Issue> parseIssues = new ArrayList<>();
        List<String> rowWarnings = new ArrayList<>();
        String fullName = normalizedText(value(row, "NOMBRE EXTERNO"), 250);
        String profile = normalizedText(value(row, "PERFIL"), 200);
        String technology = normalizedText(value(row, "TECNOLOGÍA EN LA QUE SE CERTIFICA"), 200);
        String technologicalProfile = normalizedText(value(row, "PERFIL TECNOLOGICO"), 200);
        LocalDate admission = parseDate(value(row, "FECHA DE ALTA"), "FECHA DE ALTA",
                row.rowNumber(), parseIssues, false);
        if (admission == null) {
            rowWarnings.add("El colaborador no tiene fecha de alta. Las fechas límite que dependan de este dato permanecerán pendientes.");
        }

        String email = firstValue(row, "CORREO", "EMAIL", "CORREO ELECTRONICO", "CORREO ELECTRÓNICO");
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        if (email != null && (!EMAIL.matcher(email).matches() || email.length() > 254)) {
            parseIssues.add(new Issue(row.rowNumber(), "STUDENT_IMPORT_EMAIL_INVALID",
                    "El correo informado en el Excel no tiene un formato válido y no se utilizará para identificar al colaborador."));
            email = null;
        }
        if (profile == null) rowWarnings.add("El perfil no viene informado.");
        if (technology == null) rowWarnings.add("La tecnología principal no viene informada.");
        if (technologicalProfile == null) rowWarnings.add("El perfil tecnológico no viene informado.");

        NameParts names = splitName(fullName, row.rowNumber(), parseIssues);
        CatalogRef profileRef = resolveCatalog(catalogs.profiles(), profile);
        CatalogRef techProfileRef = resolveCatalog(catalogs.technologicalProfiles(), technologicalProfile);
        if (profile != null && profileRef == null) {
            rowWarnings.add("El perfil '" + profile
                    + "' se creará en los catálogos de la organización al confirmar.");
        }
        if (technologicalProfile != null && techProfileRef == null) {
            rowWarnings.add("El perfil tecnológico '" + technologicalProfile
                    + "' se creará en los catálogos de la organización al confirmar.");
        }

        Map<String, CertificationData> certifications = new LinkedHashMap<>();
        certifications.put("DEVELOPMENT_SECURITY", certification(row, "DEVELOPMENT_SECURITY", "¿APLICA DS?",
                "ESTATUS CERTIFICACIÓN DS", "ESTATUS DEL EXAMEN DS", "FECHA DE APLICACIÓN DS",
                "PROMEDIO DS", "INTENTO DS", null, admission, catalogs.policy("DEVELOPMENT_SECURITY"),
                row.rowNumber(), parseIssues, rowWarnings));
        certifications.put("TECHNOLOGICAL", certification(row, "TECHNOLOGICAL", "¿APLICA TECNOLOGICA?",
                "ESTATUS CERTIFICACIÓN", "ESTATUS DEL EXAMEN", "FECHA DE APLICACIÓN TEC",
                "PROMEDIO", "INTENTO", null, admission, catalogs.policy("TECHNOLOGICAL"),
                row.rowNumber(), parseIssues, rowWarnings));
        certifications.put("NORMATIVE_TESTING", certification(row, "NORMATIVE_TESTING", "¿APLICA NORMATIVA?",
                "ESTATUS CERTIFICACIÓN NORMATIVA", "ESTATUS DEL EXAMEN NORMATIVA", "FECHA DE APLICACIÓN NORMATIVA",
                "PROMEDIO NORMATIVA", null, "LIMITE PARA NORMATIVA", admission, catalogs.policy("NORMATIVE_TESTING"),
                row.rowNumber(), parseIssues, rowWarnings));
        certifications.put("ONE", certification(row, "ONE", "¿APLICA ONE?", "ESTATUS CERTIFICACIÓN ONE",
                null, null, null, null, null, admission, catalogs.policy("ONE"),
                row.rowNumber(), parseIssues, rowWarnings));
        certifications.put("AGILE", certification(row, "AGILE", "¿APLICA AGILE?", "ESTATUS CERTIFICACIÓN AGILE",
                null, null, null, null, null, admission, catalogs.policy("AGILE"),
                row.rowNumber(), parseIssues, rowWarnings));
        certifications.put("JIRA", certification(row, "JIRA", "APLICA JIRA", "ESTATUS DE VALORACIÓN JIRA",
                null, null, null, null, null, admission, catalogs.policy("JIRA"),
                row.rowNumber(), parseIssues, rowWarnings));

        if (!organization.appliesCertifications() && certifications.values().stream().anyMatch(CertificationData::applies)) {
            rowWarnings.add("La organización no tiene habilitada la gestión de certificaciones; la información de certificaciones de esta fila no se aplicará.");
            certifications.replaceAll((type, value) -> emptyCertification(type));
        }
        if (technology != null && resolveQuestionTechnologyPublicId(organization.id(), technology) == null) {
            rowWarnings.add("La tecnología principal '" + technology
                    + "' se creará en los catálogos de la organización al confirmar.");
        }

        List<StudentExperienceService.ImportedItem> current = parseExperience(
                valueStarting(row, "TECNOLOGIA EN LA QUE DESARROLLA ACTUALMENTE"));
        List<StudentExperienceService.ImportedItem> languages = parseExperience(valueStarting(row, "LENGUAJES"));
        List<StudentExperienceService.ImportedItem> known = parseExperience(valueStarting(row, "TECNOLOGIAS CONOCIDAS"));
        String rowKey = "ROW-" + row.rowNumber();
        String normalizedName = fullName == null ? null : StudentExperienceService.normalizeKey(fullName);
        String normalizedEmail = email == null ? null : email.toLowerCase(Locale.ROOT);
        String matchKey = normalizedEmail != null ? "EMAIL:" + normalizedEmail
                : normalizedName == null || normalizedName.isBlank() ? null : "NAME:" + normalizedName;
        String subject = fullName == null || fullName.isBlank()
                ? "Fila " + row.rowNumber() : "Colaborador " + fullName;
        List<Issue> contextualWarnings = new ArrayList<>();
        for (Issue issue : parseIssues) {
            contextualWarnings.add(new Issue(issue.row(), issue.code(), subject + ": " + issue.message()));
            rowWarnings.add(issue.message());
        }
        ImportedStudent student = new ImportedStudent(rowKey, row.rowNumber(), fullName, names.firstName(),
                names.lastName(), normalizedName, email, normalizedEmail, matchKey, profile,
                profileRef == null ? null : profileRef.publicId(), admission, technology, technologicalProfile,
                techProfileRef == null ? null : techProfileRef.publicId(), certificationLevel(profile),
                Map.copyOf(certifications), current, languages, known, List.copyOf(new LinkedHashSet<>(rowWarnings)), false);
        return new ParseResult(student, List.of(), List.copyOf(contextualWarnings));
    }

    private CertificationData certification(XlsxCertificationReader.RowData row, String type, String appliesHeader,
            String statusHeader, String examHeader, String applicationHeader, String scoreHeader,
            String attemptHeader, String deadlineHeader, LocalDate admission, CertificationPolicy policy,
            int rowNumber, List<Issue> errors, List<String> warnings) {
        Boolean appliesValue = parseBoolean(value(row, appliesHeader), appliesHeader, rowNumber, errors);
        boolean applies = Boolean.TRUE.equals(appliesValue);
        String rawCertificationStatus = statusHeader == null ? null : value(row, statusHeader);
        String certificationStatus = statusHeader == null ? null
                : normalizeImportCertificationStatus(type, rawCertificationStatus);
        String examStatus = examHeader == null ? null : normalizeImportExamStatus(value(row, examHeader));
        LocalDate application = applicationHeader == null ? null
                : parseDate(value(row, applicationHeader), applicationHeader, rowNumber, errors, false);
        BigDecimal score = scoreHeader == null ? null
                : parseScore(value(row, scoreHeader), scoreHeader, rowNumber, errors);
        Integer administrativeFailures = attemptHeader == null ? null
                : parseAttempt(value(row, attemptHeader), attemptHeader, rowNumber, errors);
        LocalDate calculated = calculatedDeadline(type, admission, policy);
        LocalDate importedDeadline = deadlineHeader == null ? null
                : parseDate(value(row, deadlineHeader), deadlineHeader, rowNumber, errors, false);
        LocalDate deadline = applies && !nonExpiring(type)
                ? resolveImportDeadline(importedDeadline, calculated) : null;

        boolean statusWasNoApply = isNoApplyStatus(rawCertificationStatus);
        if (!hasMeaningfulImportStatus(certificationStatus)) certificationStatus = null;
        if (!hasMeaningfulImportStatus(examStatus)) examStatus = null;
        boolean hasTrackingData = hasImportTrackingData(
                certificationStatus, examStatus, application, score, administrativeFailures);
        String internalExamStatus = internalExamStatus(examStatus);
        boolean statusIndicatesPriorApproval = statusIndicatesPriorApproval(certificationStatus);
        boolean latestExamPassed = "PASSED".equals(internalExamStatus);
        boolean latestExamFailed = "FAILED".equals(internalExamStatus);
        Boolean approved = statusIndicatesPriorApproval || latestExamPassed
                ? Boolean.TRUE
                : latestExamFailed || statusIndicatesNotApproved(certificationStatus) ? Boolean.FALSE : null;
        LocalDate lastApprovedApplicationDate = latestExamPassed
                || (statusIndicatesPriorApproval && !"FAILED".equals(internalExamStatus))
                ? application : null;
        boolean explicitDeadline = importedDeadline != null;
        if (lastApprovedApplicationDate != null && !explicitDeadline) {
            deadline = null;
        }

        if (!applies && hasTrackingData) {
            errors.add(new Issue(rowNumber, "STUDENT_IMPORT_NOT_APPLICABLE_WITH_TRACKING",
                    typeLabel(type) + ": está marcada como No aplica, pero contiene fechas, resultados o intentos."));
        }
        if (applies && statusWasNoApply) {
            errors.add(new Issue(rowNumber, "STUDENT_IMPORT_APPLICABILITY_STATUS_CONFLICT",
                    typeLabel(type) + ": la aplicabilidad indica Sí, pero el estatus indica No aplica."));
        }
        if (applies && !nonExpiring(type)) {
            if (requiresImportApplicationDate(type, true, approved, certificationStatus) && application == null) {
                String requiredHeader = applicationHeader == null ? "FECHA DE APLICACIÓN" : applicationHeader;
                errors.add(new Issue(rowNumber, "STUDENT_IMPORT_CERTIFICATION_APPLICATION_DATE_REQUIRED",
                        typeLabel(type) + ": " + requiredHeader
                                + " es obligatoria cuando el estado indica aprobación o vigencia."));
            }
            if (application != null && certificationStatus == null && examStatus == null) {
                errors.add(new Issue(rowNumber, "STUDENT_IMPORT_APPLICATION_WITHOUT_STATUS",
                        typeLabel(type) + ": existe fecha de aplicación, pero no existe estatus de examen o certificación."));
            }
            if (score != null && application == null) {
                errors.add(new Issue(rowNumber, "STUDENT_IMPORT_SCORE_WITHOUT_APPLICATION",
                        typeLabel(type) + ": existe promedio, pero falta la fecha de aplicación."));
            }
            if (administrativeFailures != null && administrativeFailures == 0
                    && latestExamFailed) {
                errors.add(new Issue(rowNumber, "STUDENT_IMPORT_FAILED_WITH_ZERO_ATTEMPT",
                        typeLabel(type) + ": un resultado reprobado no puede tener intento administrativo 0."));
            }
            if (administrativeFailures != null
                    && application == null && certificationStatus == null && examStatus == null && score == null) {
                errors.add(new Issue(rowNumber, "STUDENT_IMPORT_ATTEMPT_WITHOUT_PRESENTATION",
                        typeLabel(type) + ": se informó una reprobación previa sin evidencia de presentación."));
            }
        }

        if (!applies) {
            certificationStatus = null;
            examStatus = null;
            application = null;
            score = null;
            administrativeFailures = null;
            deadline = null;
            explicitDeadline = false;
            approved = null;
            lastApprovedApplicationDate = null;
        }
        if (nonExpiring(type)) {
            application = null;
            lastApprovedApplicationDate = null;
            score = null;
            administrativeFailures = null;
            deadline = null;
            explicitDeadline = false;
        }

        LocalDate expiration = expirationForImport(type, lastApprovedApplicationDate, policy);
        String validity = validityStatus(expiration, approved);
        String tracking = trackingStatus(applies, certificationStatus, examStatus, approved);
        String comparableStatus = certificationStatusLabel(applies, tracking, validity, approved);
        String processType = processTypeForImport(type, lastApprovedApplicationDate);
        LocalDate referenceDate = "RECERTIFICATION".equals(processType)
                ? lastApprovedApplicationDate : admission;
        return new CertificationData(type, applies, comparableStatus, certificationStatus, examStatus, internalExamStatus,
                application, lastApprovedApplicationDate, score, administrativeFailures, deadline, explicitDeadline,
                expiration, approved, validity, tracking,
                processType, referenceDate, applies ? "IMPORT" : null);
    }

    static int validityYears(String type, CertificationPolicy policy) {
        if ("TECHNOLOGICAL".equals(type)) return 2;
        if ("DEVELOPMENT_SECURITY".equals(type) || "NORMATIVE_TESTING".equals(type)) return 1;
        if (policy != null && policy.validityYears() != null) return policy.validityYears();
        return 1;
    }

    static LocalDate expirationForImport(String type, LocalDate lastApprovedApplicationDate) {
        return expirationForImport(type, lastApprovedApplicationDate, null);
    }

    private static LocalDate expirationForImport(String type, LocalDate lastApprovedApplicationDate,
            CertificationPolicy policy) {
        if (lastApprovedApplicationDate == null || nonExpiring(type)) return null;
        return CertificationLifecycleCalculator.expiration(
                lastApprovedApplicationDate, validityYears(type, policy));
    }

    static String processTypeForImport(String type, LocalDate lastApprovedApplicationDate) {
        return nonExpiring(type) || lastApprovedApplicationDate == null
                ? "CERTIFICATION" : "RECERTIFICATION";
    }

    private Reconciliation reconcileCertificationLifecycle(ImportedStudent imported,
            Map<String, CertificationData> currentCertifications) {
        if (imported == null) return new Reconciliation(null, List.of(), List.of());
        Map<String, CertificationData> reconciled = new LinkedHashMap<>(imported.certifications());
        List<ConflictPreview> conflicts = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        for (String type : CERT_TYPES) {
            CertificationData incoming = reconciled.get(type);
            if (incoming == null || !incoming.applies() || nonExpiring(type)) continue;
            CertificationData current = currentCertifications.get(type);
            LocalDate lastApproved = incoming.lastApprovedApplicationDate() != null
                    ? incoming.lastApprovedApplicationDate()
                    : current == null ? null : current.lastApprovedApplicationDate();
            boolean recertification = Boolean.TRUE.equals(incoming.approved()) || lastApproved != null;
            if (recertification && lastApproved == null) {
                warnings.add(new Issue(imported.rowNumber(), "STUDENT_IMPORT_RECERTIFICATION_REFERENCE_PENDING",
                        collaboratorLabel(imported) + ": " + typeLabel(type)
                                + " no tiene una fecha de aplicación aprobada para calcular el vencimiento."));
            }
            LocalDate deadline = recertification && !incoming.explicitDeadline()
                    ? null : incoming.deadline();
            LocalDate expiration = expirationForImport(type, lastApproved, null);
            String validity = validityStatus(expiration, recertification ? Boolean.TRUE : incoming.approved());
            Boolean everApproved = recertification ? Boolean.TRUE : incoming.approved();
            String processType = recertification ? "RECERTIFICATION" : "CERTIFICATION";
            LocalDate referenceDate = recertification ? lastApproved : imported.admissionDate();
            String platformTracking = "EXPIRED".equals(validity) ? "EXPIRED" : incoming.trackingStatus();
            String platformStatus = certificationStatusLabel(
                    incoming.applies(), platformTracking, validity, everApproved);
            CertificationData platformData = new CertificationData(type, incoming.applies(), platformStatus,
                    incoming.excelCertificationStatus(), incoming.examStatus(), incoming.internalExamStatus(),
                    incoming.applicationDate(), lastApproved, incoming.score10(), incoming.attempt(), deadline,
                    incoming.explicitDeadline(), expiration, everApproved, validity, platformTracking,
                    processType, referenceDate, incoming.resultSource());
            reconciled.put(type, platformData);

            String excelValidity = excelValidityStatus(incoming.excelCertificationStatus());
            if (excelValidity != null && !Objects.equals(excelValidity, validity)) {
                String conflictId = conflictId(imported.rowKey(), "CERTIFICATION_VALIDITY", type);
                String reason = "El Excel indica '" + text(incoming.excelCertificationStatus())
                        + "', pero la fecha de aplicación " + text(lastApproved)
                        + " produce vencimiento " + text(expiration)
                        + " y el estado calculado es '" + platformStatus + "'.";
                conflicts.add(new ConflictPreview(conflictId, imported.rowKey(), imported.rowNumber(),
                        collaboratorName(imported), "STUDENT_IMPORT_CERTIFICATION_VALIDITY_CONFLICT",
                        "CERTIFICATION_VALIDITY:" + type,
                        "Conflicto de vigencia en " + typeLabel(type),
                        "Estatus de certificación", type, typeLabel(type),
                        text(incoming.excelCertificationStatus()),
                        current == null ? "Sin registro" : text(current.certificationStatus()),
                        platformStatus, reason,
                        List.of(
                                new ConflictAction("USE_PLATFORM", "Aplicar cálculo de la plataforma",
                                        "Conserva la fecha de aplicación y aplica el estatus calculado."),
                                new ConflictAction("USE_EXCEL", "Conservar estatus del Excel",
                                        "Conserva la fecha de aplicación y aplica el estatus informado en el archivo."),
                                new ConflictAction("OMIT_ROW", "Omitir este colaborador",
                                        "No aplica cambios de esta fila y continúa con los demás colaboradores.")),
                        null, false));
            }
        }
        ImportedStudent result = imported.withCertificationsAndWarnings(
                Map.copyOf(reconciled), imported.warnings(), false);
        return new Reconciliation(result, List.copyOf(conflicts), List.copyOf(warnings));
    }

    static String excelValidityStatus(String certificationStatus) {
        if (certificationStatus == null || certificationStatus.isBlank()) return null;
        String normalized = StudentExperienceService.normalizeKey(certificationStatus);
        if (normalized.contains("VENCID") || normalized.contains("FUERA DE NORMA")) return "EXPIRED";
        if (normalized.contains("PROXIMA A VENCER")) return "EXPIRING_SOON";
        if (normalized.startsWith("VIGENTE")) return "VALID";
        return null;
    }

    private String conflictFingerprint(ImportedStudent imported, ConflictPreview conflict) {
        if (!REUSABLE_CERTIFICATION_CONFLICT.equals(conflict.code())
                || conflict.certificationType() == null) {
            return null;
        }
        CertificationData certification = imported.certifications().get(conflict.certificationType());
        return conflictDecisionFingerprint(conflict.code(), conflict.certificationType(),
                conflict.excelValue(), certification == null ? null : certification.applicationDate(),
                certification == null ? null : certification.expiration(), conflict.calculatedValue());
    }

    static String conflictDecisionFingerprint(String conflictCode, String certificationType,
            String excelValue, LocalDate applicationDate, LocalDate expirationDate,
            String calculatedValue) {
        String canonical = String.join("|",
                text(conflictCode),
                text(certificationType),
                canonicalConflictValue(excelValue),
                text(applicationDate),
                text(expirationDate),
                canonicalConflictValue(calculatedValue));
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalConflictValue(String value) {
        return value == null ? "" : StudentExperienceService.normalizeKey(value);
    }

    private static String conflictId(String rowKey, String code, String type) {
        return rowKey + ":" + code + (type == null ? "" : ":" + type);
    }

    private static String collaboratorName(ImportedStudent imported) {
        return blank(imported.fullName()) ? "Fila " + imported.rowNumber() : imported.fullName();
    }

    private static String collaboratorLabel(ImportedStudent imported) {
        return blank(imported.fullName()) ? "Fila " + imported.rowNumber() : "Colaborador " + imported.fullName();
    }

    private ConflictPreview omissionConflict(ImportedStudent imported, String code, String title, String reason) {
        return new ConflictPreview(conflictId(imported.rowKey(), code, null), imported.rowKey(),
                imported.rowNumber(), collaboratorName(imported), code, code, title, "Identificación",
                null, null, text(imported.fullName()), "Sin coincidencia única", "Omitir la fila",
                reason, List.of(new ConflictAction("OMIT_ROW", "Omitir este colaborador",
                        "No aplica cambios de esta fila y continúa con los demás colaboradores.")),
                null, false);
    }

    private ImportedStudent resolveConflictDecisions(ImportedStudent imported, PendingImport state,
            Map<String, String> resolutions) {
        ImportedStudent resolved = imported;
        for (ConflictPreview conflict : state.conflicts().values()) {
            if (!conflict.rowKey().equals(imported.rowKey())) continue;
            String action = resolutions.get(conflict.id());
            if ("OMIT_ROW".equals(action)) return null;
            if ("USE_EXCEL".equals(action) && conflict.certificationType() != null) {
                CertificationData current = resolved.certifications().get(conflict.certificationType());
                if (current != null) {
                    Map<String, CertificationData> certifications = new LinkedHashMap<>(resolved.certifications());
                    certifications.put(conflict.certificationType(), applyExcelLifecycleChoice(current));
                    resolved = resolved.withCertificationsAndWarnings(
                            Map.copyOf(certifications), resolved.warnings(), false);
                }
            }
        }
        return resolved;
    }

    private CertificationData applyExcelLifecycleChoice(CertificationData current) {
        String excelStatus = current.excelCertificationStatus();
        String excelValidity = excelValidityStatus(excelStatus);
        if (excelValidity == null) return current;
        String tracking = "EXPIRED".equals(excelValidity)
                ? "EXPIRED" : trackingStatus(current.applies(), excelStatus, current.examStatus(), current.approved());
        return new CertificationData(current.type(), current.applies(), excelStatus, excelStatus,
                current.examStatus(), current.internalExamStatus(), current.applicationDate(),
                current.lastApprovedApplicationDate(), current.score10(), current.attempt(), current.deadline(),
                current.explicitDeadline(), current.expiration(), current.approved(), excelValidity, tracking,
                current.processType(), current.referenceDate(), current.resultSource());
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
        addBooleanChange(changes, "applies:JIRA", "Aplica Jira", current.appliesJira(), imported.flag("JIRA"));
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
        if (!after.applies()) {
            return;
        }
        String prefix = "cert:" + type + ":";
        String label = typeLabel(type);
        addChange(changes, prefix + "status", label + " — Estado de certificación",
                text(before.certificationStatus()), text(after.certificationStatus()));
        if (!nonExpiring(type)) {
            if (!Objects.equals(before.internalExamStatus(), after.internalExamStatus())) {
                changes.add(new FieldChange(prefix + "examStatus", label + " — Estado del examen",
                        text(before.examStatus()), text(after.examStatus()), true));
            }
            addChange(changes, prefix + "applicationDate", label + " — Última fecha de presentación",
                    text(before.applicationDate()), text(after.applicationDate()));
            addChange(changes, prefix + "lastApprovedApplicationDate",
                    label + " — Última fecha de aplicación aprobada",
                    text(before.lastApprovedApplicationDate()), text(after.lastApprovedApplicationDate()));
            addDecimalChange(changes, prefix + "score", label + " — Promedio",
                    before.score10(), after.score10());
            if (importsAttempt(type)) {
                addChange(changes, prefix + "failureCount", label + " — Reprobaciones administrativas",
                        text(before.attempt()), text(after.attempt()));
            }
            addChange(changes, prefix + "processType", label + " — Tipo de proceso",
                    processLabel(before.processType()), processLabel(after.processType()));
            addChange(changes, prefix + "referenceDate", label + " — Fecha de referencia",
                    text(before.referenceDate()), text(after.referenceDate()));
            addChange(changes, prefix + "deadline", label + " — Fecha límite inicial",
                    text(before.deadline()), text(after.deadline()));
            addChange(changes, prefix + "expiration", label + " — Fecha de vencimiento",
                    text(before.expiration()), text(after.expiration()));
            addChange(changes, prefix + "lifecycle", label + " — Vigencia calculada",
                    lifecycleSummary(before), lifecycleSummary(after));
        }
    }

    private CertificationData emptyCertification(String type) {
        return new CertificationData(type, false, null, null, null, "NOT_SCHEDULED", null, null, null, null, null,
                false, null, null, "NOT_OBTAINED", "PENDING", "CERTIFICATION", null, null);
    }

    private List<ExistingStudent> existingStudents(Long organizationId) {
        return jdbc.query("""
            SELECT s.STUDENT_ID, s.PUBLIC_ID, s.STUDENT_CODE, s.EMAIL, s.NORMALIZED_EMAIL,
                   s.FIRST_NAME, s.LAST_NAME, s.DISPLAY_NAME, s.STATUS,
                   s.ACCESS_VALID_FROM, s.ACCESS_EXPIRES_ON, s.ADMISSION_DATE,
                   COALESCE(
                       student_technology.TECHNOLOGY_NAME,
                       (SELECT MAX(t.TECHNOLOGY_NAME) KEEP (DENSE_RANK FIRST ORDER BY c.IS_PRIMARY DESC,
                                    c.UPDATED_AT DESC, c.STUDENT_CERTIFICATION_CYCLE_ID DESC)
                          FROM STUDENT_CERTIFICATION_CYCLE c
                          JOIN QUESTION_TECHNOLOGY t ON t.TECHNOLOGY_ID = c.TECHNOLOGY_ID
                         WHERE c.STUDENT_ID = s.STUDENT_ID
                           AND c.ORGANIZATION_ID = s.ORGANIZATION_ID
                           AND c.CERTIFICATION_TYPE = 'TECHNOLOGICAL'
                           AND c.ACTIVE = 1)) PRIMARY_CERT_TECH_NAME,
                   s.PROFESSIONAL_PROFILE_ID, p.PUBLIC_ID PROFILE_PUBLIC_ID, p.PROFILE_NAME,
                   s.TECHNOLOGICAL_PROFILE_ID, tp.PUBLIC_ID TECH_PROFILE_PUBLIC_ID,
                   tp.PROFILE_NAME TECH_PROFILE_NAME,
                   s.APPLIES_TECH_CERT, s.APPLIES_DEV_SECURITY, s.APPLIES_NORMATIVE_TESTING,
                   s.APPLIES_ONE, s.APPLIES_AGILE, s.APPLIES_JIRA, s.VERSION_NO, s.ORGANIZATION_ID
              FROM STUDENT s
              LEFT JOIN QUESTION_TECHNOLOGY student_technology
                ON student_technology.TECHNOLOGY_ID = s.TECHNOLOGY_ID
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
                rs.getBoolean("APPLIES_AGILE"), rs.getBoolean("APPLIES_JIRA"), rs.getLong("VERSION_NO"));
    }

    private Map<ConflictDecisionKey, String> existingConflictDecisions(Long organizationId) {
        Map<ConflictDecisionKey, String> result = new HashMap<>();
        jdbc.query("""
            SELECT STUDENT_ID, CERTIFICATION_TYPE, CONFLICT_CODE,
                   CONFLICT_FINGERPRINT, ACTION_CODE
              FROM STUDENT_IMPORT_CONFLICT_DECISION
             WHERE ORGANIZATION_ID = :organizationId
               AND CONFLICT_CODE = :conflictCode
            """, new MapSqlParameterSource("organizationId", organizationId)
                .addValue("conflictCode", REUSABLE_CERTIFICATION_CONFLICT), rs -> {
                    ConflictDecisionKey key = new ConflictDecisionKey(
                            rs.getLong("STUDENT_ID"), rs.getString("CERTIFICATION_TYPE"),
                            rs.getString("CONFLICT_CODE"), rs.getString("CONFLICT_FINGERPRINT"));
                    result.put(key, rs.getString("ACTION_CODE"));
                });
        return Map.copyOf(result);
    }

    private Map<Long, Map<String, String>> existingImportFingerprints(Long organizationId) {
        Map<Long, Map<String, String>> result = new HashMap<>();
        jdbc.query("""
            WITH ranked_cycles AS (
                SELECT c.STUDENT_ID, c.CERTIFICATION_TYPE, c.LAST_IMPORT_FINGERPRINT,
                       ROW_NUMBER() OVER (
                           PARTITION BY c.STUDENT_ID, c.CERTIFICATION_TYPE
                           ORDER BY c.ACTIVE DESC, c.IS_PRIMARY DESC, c.UPDATED_AT DESC,
                                    c.STUDENT_CERTIFICATION_CYCLE_ID DESC) RN
                  FROM STUDENT_CERTIFICATION_CYCLE c
                 WHERE c.ORGANIZATION_ID = :organizationId
            )
            SELECT STUDENT_ID, CERTIFICATION_TYPE, LAST_IMPORT_FINGERPRINT
              FROM ranked_cycles
             WHERE RN = 1
               AND LAST_IMPORT_FINGERPRINT IS NOT NULL
            """, Map.of("organizationId", organizationId), rs -> {
                result.computeIfAbsent(rs.getLong("STUDENT_ID"), ignored -> new HashMap<>())
                        .put(rs.getString("CERTIFICATION_TYPE"), rs.getString("LAST_IMPORT_FINGERPRINT"));
            });
        result.replaceAll((key, value) -> Map.copyOf(value));
        return Map.copyOf(result);
    }

    private String inferPreviousConflictAction(ImportedStudent imported, ConflictPreview conflict,
            String previousImportFingerprint, Long organizationId) {
        if (previousImportFingerprint == null || previousImportFingerprint.isBlank()
                || !REUSABLE_CERTIFICATION_CONFLICT.equals(conflict.code())
                || conflict.certificationType() == null) {
            return null;
        }
        CertificationData platformChoice = imported.certifications().get(conflict.certificationType());
        if (platformChoice == null) return null;

        String technologyPublicId = null;
        CertificationLevel level = null;
        if (CertificationType.TECHNOLOGICAL.name().equals(conflict.certificationType())) {
            technologyPublicId = resolveQuestionTechnologyPublicId(organizationId, imported.primaryTechnology());
            if (technologyPublicId == null || imported.certificationLevel() == null) return null;
            try {
                level = CertificationLevel.valueOf(imported.certificationLevel());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        String platformFingerprint = certificationFingerprint(platformChoice, technologyPublicId, level);
        CertificationData excelChoice = applyExcelLifecycleChoice(platformChoice);
        String excelFingerprint = certificationFingerprint(excelChoice, technologyPublicId, level);
        return inferConflictActionFromImportFingerprint(
                previousImportFingerprint, platformFingerprint, excelFingerprint);
    }

    static String inferConflictActionFromImportFingerprint(String previousImportFingerprint,
            String platformFingerprint, String excelFingerprint) {
        if (previousImportFingerprint == null || previousImportFingerprint.isBlank()) return null;
        if (previousImportFingerprint.equals(platformFingerprint)) return "USE_PLATFORM";
        if (previousImportFingerprint.equals(excelFingerprint)) return "USE_EXCEL";
        return null;
    }

    private Map<Long, Map<String, CertificationData>> existingCertifications(Long organizationId) {
        Map<Long, Map<String, CertificationData>> result = new HashMap<>();
        jdbc.query("""
            WITH ranked_cycles AS (
                SELECT c.*,
                       MAX(c.LAST_APPROVED_APPLICATION_DATE) OVER (
                           PARTITION BY c.STUDENT_ID, c.CERTIFICATION_TYPE
                       ) HISTORICAL_LAST_APPROVED_DATE,
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
                       WHEN 'JIRA' THEN s.APPLIES_JIRA
                   END APPLIES,
                   c.TRACKING_STATUS, c.PROCESS_TYPE, c.DEADLINE_DATE, s.ADMISSION_DATE,
                   NVL(c.APPLICATION_DATE, a.APPLICATION_DATE) APPLICATION_DATE,
                   COALESCE(c.LAST_APPROVED_APPLICATION_DATE, c.HISTORICAL_LAST_APPROVED_DATE)
                       LAST_APPROVED_APPLICATION_DATE,
                   c.EXPIRATION_DATE, c.APPROVED, c.VALIDITY_STATUS,
                   COALESCE(c.LATEST_EXAM_STATUS, a.EXAM_STATUS, 'NOT_SCHEDULED') EXAM_STATUS,
                   COALESCE(c.LATEST_SCORE, a.SCORE) SCORE, c.IMPORTED_FAILURE_COUNT, c.RESULT_SOURCE
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
                        null, examStatusLabel(exam), exam,
                        localDate(rs, "APPLICATION_DATE"), localDate(rs, "LAST_APPROVED_APPLICATION_DATE"),
                        rs.getBigDecimal("SCORE"), nullableInteger(rs, "IMPORTED_FAILURE_COUNT"),
                        localDate(rs, "DEADLINE_DATE"), false,
                        localDate(rs, "EXPIRATION_DATE"), approved, rs.getString("VALIDITY_STATUS"), tracking,
                        rs.getString("PROCESS_TYPE"),
                        "RECERTIFICATION".equals(rs.getString("PROCESS_TYPE"))
                                ? localDate(rs, "LAST_APPROVED_APPLICATION_DATE")
                                : localDate(rs, "ADMISSION_DATE"),
                        rs.getString("RESULT_SOURCE"));
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
        if (selections != null) selections.forEach(value -> {
            if (value != null && value.rowKey() != null) result.put(value.rowKey(), value);
        });
        return result;
    }

    private Map<String, ChangeSelection> indexChanges(List<ChangeSelection> selections) {
        Map<String, ChangeSelection> result = new HashMap<>();
        if (selections != null) selections.forEach(value -> {
            if (value != null && value.studentPublicId() != null) result.put(value.studentPublicId(), value);
        });
        return result;
    }

    private Map<String, LowSelection> indexLows(List<LowSelection> selections) {
        Map<String, LowSelection> result = new HashMap<>();
        if (selections != null) selections.forEach(value -> {
            if (value != null && value.studentPublicId() != null) result.put(value.studentPublicId(), value);
        });
        return result;
    }

    private Map<String, String> indexConflictResolutions(List<ConflictResolution> resolutions) {
        Map<String, String> result = new HashMap<>();
        if (resolutions != null) resolutions.forEach(value -> {
            if (value != null && value.conflictId() != null && value.action() != null) {
                result.put(value.conflictId(), value.action());
            }
        });
        return result;
    }

    private Map<String, String> effectiveConflictResolutions(PendingImport state,
            Map<String, String> submitted) {
        Map<String, String> result = new HashMap<>(submitted);
        state.automaticConflictResolutions().forEach(result::put);
        return Map.copyOf(result);
    }

    private Map<String, ChangeSelection> includeConflictFields(PendingImport state,
            Map<String, ChangeSelection> selections, Map<String, String> resolutions) {
        Map<String, ChangeSelection> result = new HashMap<>(selections);
        for (ConflictPreview conflict : state.conflicts().values()) {
            String action = resolutions.get(conflict.id());
            if (conflict.certificationType() == null || "OMIT_ROW".equals(action)) continue;
            Match match = state.matches().get(conflict.rowKey());
            if (match == null || match.existing() == null) continue;
            String studentPublicId = match.existing().publicId();
            ChangeSelection current = result.get(studentPublicId);
            Set<String> fields = new HashSet<>(current == null ? Set.of() : current.fields());
            String prefix = "cert:" + conflict.certificationType() + ":";
            fields.add(prefix + "status");
            fields.add(prefix + "applicationDate");
            fields.add(prefix + "lifecycle");
            result.put(studentPublicId, new ChangeSelection(studentPublicId, fields));
        }
        return result;
    }

    private void validateSelections(PendingImport state, Map<String, NewSelection> newSelections,
            Map<String, ChangeSelection> changeSelections, Map<String, LowSelection> lowSelections,
            Map<String, String> conflictResolutions) {
        if (!state.newRowKeys().containsAll(newSelections.keySet())
                || !state.allowedChangeFields().keySet().containsAll(changeSelections.keySet())
                || !state.possibleLowPublicIds().containsAll(lowSelections.keySet())
                || !state.conflicts().keySet().containsAll(conflictResolutions.keySet())) {
            throw new BusinessException("STUDENT_IMPORT_SELECTION_INVALID",
                    "La selección contiene registros que no pertenecen a la vista previa autorizada.");
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
        for (ConflictPreview conflict : state.conflicts().values()) {
            String action = conflictResolutions.get(conflict.id());
            if (action == null || action.isBlank()) {
                throw new BusinessException("STUDENT_IMPORT_CONFLICT_PENDING",
                        "Selecciona cómo proceder con el conflicto de la fila " + conflict.row() + ".");
            }
            boolean allowed = conflict.actions().stream().anyMatch(option -> option.value().equals(action));
            if (!allowed) {
                throw new BusinessException("STUDENT_IMPORT_CONFLICT_ACTION_INVALID",
                        "La decisión seleccionada para la fila " + conflict.row() + " no es válida.");
            }
        }
    }

    private String requireFullName(String value, int row) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("STUDENT_IMPORT_NAME_REQUIRED",
                    "Captura el nombre completo del nuevo colaborador de la fila " + row + ".");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 250) {
            throw new BusinessException("STUDENT_IMPORT_NAME_TOO_LONG",
                    "El nombre completo de la fila " + row + " no puede exceder 250 caracteres.");
        }
        return normalized;
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

    private NameParts splitName(String fullName, int row, List<Issue> issues) {
        if (fullName == null || fullName.isBlank()) return new NameParts("", "");
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return new NameParts(parts[0], "");
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

    static String normalizeImportCertificationStatus(String type, String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = StudentExperienceService.normalizeKey(value);
        if (Set.of("NO APLICA", "NA", "N A").contains(normalized)) return "No aplica";
        if ("JIRA".equals(type) && "FORMADO".equals(normalized)) return "Aprobada";
        if ("JIRA".equals(type) && normalized.startsWith("PENDIENTE")) return "Sin presentar";
        if (nonExpiring(type) && "SI".equals(normalized)) return "Aprobada";
        if (nonExpiring(type) && "EN TIEMPO".equals(normalized)) {
            return "Sin presentar";
        }
        if (normalized.startsWith("SIN PRESENTAR") && normalized.contains("FUERA DE NORMA")) return "Vencida";
        if (normalized.startsWith("SIN PRESENTAR")
                && (normalized.contains("PROXIMO A VENCER") || normalized.contains("PROXIMA A VENCER"))) {
            return "Sin presentar — Próxima a vencer";
        }
        if (normalized.startsWith("VIGENTE")
                && (normalized.contains("PROXIMO A VENCER") || normalized.contains("PROXIMA A VENCER"))) {
            return "Vigente — Próxima a vencer";
        }
        if (normalized.startsWith("VIGENTE")) return "Vigente — Regular";
        if (normalized.startsWith("VENCID") || normalized.contains("FUERA DE NORMA")) return "Vencida";
        if (normalized.startsWith("NO APROB") || normalized.startsWith("REPROB")) return "No aprobada";
        if (normalized.startsWith("APROB")) return "Aprobada";
        if (normalized.startsWith("SIN PRESENTAR") || normalized.equals("PENDIENTE")) return "Sin presentar";
        if (normalized.startsWith("PROGRAM")) return "Programada";
        if (normalized.startsWith("EN PROCESO")) return "En proceso";
        if (normalized.startsWith("PRESENT")) return "Presentada";
        if (normalized.startsWith("CANCEL")) return "Cancelada";
        return value.trim().replaceAll("\\s+", " ");
    }

    static String normalizeImportExamStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = StudentExperienceService.normalizeKey(value);
        if (Set.of("NO APLICA", "NA", "N A").contains(normalized)) return null;
        if (normalized.startsWith("NO APROB") || normalized.startsWith("REPROB")
                || normalized.contains("FAILED")) return "No aprobado";
        if (normalized.startsWith("APROB") || normalized.contains("PASSED")) return "Aprobado";
        if (normalized.equals("SIN EXAMEN") || normalized.startsWith("SIN PRESENTAR")
                || normalized.equals("NO PROGRAMADO") || normalized.equals("NOT SCHEDULED")) {
            return "Sin presentar";
        }
        if (normalized.startsWith("REPROGRAM")) return "Reprogramado";
        if (normalized.startsWith("PROGRAM")) return "Programado";
        if (normalized.startsWith("AUSENT")) return "Ausente";
        if (normalized.startsWith("CANCEL")) return "Cancelado";
        if (normalized.startsWith("PRESENT") || normalized.startsWith("COMPLET")) return "Presentado";
        return value.trim().replaceAll("\\s+", " ");
    }

    static boolean importsAttempt(String type) {
        return "TECHNOLOGICAL".equals(type) || "DEVELOPMENT_SECURITY".equals(type);
    }

    static boolean requiresImportApplicationDate(String type, boolean applies, Boolean approved,
            String certificationStatus) {
        return applies && !nonExpiring(type)
                && (Boolean.TRUE.equals(approved) || statusContains(certificationStatus, "VIGENTE"));
    }
    static boolean isNoApplyStatus(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = StudentExperienceService.normalizeKey(value);
        return Set.of("NO APLICA", "NA", "N A").contains(normalized);
    }

    static boolean hasMeaningfulImportStatus(String value) {
        return value != null && !value.isBlank() && !isNoApplyStatus(value);
    }
    static boolean hasImportTrackingData(String certificationStatus, String examStatus,
            LocalDate application, BigDecimal score, Integer attempt) {
        return hasMeaningfulImportStatus(certificationStatus)
                || hasMeaningfulImportStatus(examStatus)
                || application != null || score != null || attempt != null;
    }
    static LocalDate resolveImportDeadline(LocalDate importedDeadline, LocalDate calculatedDeadline) {
        return importedDeadline == null ? calculatedDeadline : importedDeadline;
    }
    static boolean statusIndicatesPriorApproval(String certificationStatus) {
        String normalized = StudentExperienceService.normalizeKey(certificationStatus);
        return normalized.startsWith("APROB")
                || normalized.startsWith("VIGENTE")
                || normalized.startsWith("VENCID");
    }

    static boolean statusIndicatesNotApproved(String certificationStatus) {
        String normalized = StudentExperienceService.normalizeKey(certificationStatus);
        return normalized.startsWith("NO APROB") || normalized.startsWith("REPROB");
    }

    private String trackingStatus(boolean applies, String certificationStatus, String examStatus, Boolean approved) {
        if (!applies) return "CANCELLED";
        String value = StudentExperienceService.normalizeKey((certificationStatus == null ? "" : certificationStatus)
                + " " + (examStatus == null ? "" : examStatus));
        if (value.contains("VENCID") || value.contains("FUERA DE NORMA")) return "EXPIRED";
        if (Boolean.TRUE.equals(approved)) return "APPROVED";
        if (Boolean.FALSE.equals(approved)) return "NOT_APPROVED";
        if (value.contains("SIN PRESENTAR") || value.contains("PENDIENTE")
                || value.contains("EN TIEMPO")) return "PENDING";
        if (value.contains("PROGRAM") || value.contains("SCHEDULE")) return "SCHEDULED";
        if (value.contains("PRESENT") || value.contains("APLIC")) return "APPLIED";
        return "PENDING";
    }

    static String internalExamStatus(String value) {
        String normalized = StudentExperienceService.normalizeKey(value);
        if (normalized.contains("NO APROB") || normalized.contains("REPROB")
                || normalized.contains("FAILED")) return "FAILED";
        if (normalized.contains("APROB") || normalized.contains("PASSED")) return "PASSED";
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
        if (admission == null || nonExpiring(type) || policy == null
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
    private static String processLabel(String value) {
        return "RECERTIFICATION".equals(value) ? "Recertificación" : "Certificación inicial";
    }

    private static String lifecycleSummary(CertificationData value) {
        if (value == null || !value.applies()) return "No aplica";
        return text(value.validityStatus()) + (value.expiration() == null ? "" : " · Vence " + value.expiration());
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
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
    private static boolean nonExpiring(String type) {
        return "ONE".equals(type) || "AGILE".equals(type) || "JIRA".equals(type);
    }
    private static String typeLabel(String type) {
        return switch (type) {
            case "TECHNOLOGICAL" -> "Certificación tecnológica";
            case "DEVELOPMENT_SECURITY" -> "Desarrollo Seguro";
            case "NORMATIVE_TESTING" -> "Normativa y Testing";
            case "ONE" -> "ONE";
            case "AGILE" -> "Agile";
            case "JIRA" -> "Jira";
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
    private record ParseResult(ImportedStudent student, List<Issue> errors, List<Issue> warnings) {}
    private record Reconciliation(ImportedStudent student, List<ConflictPreview> conflicts, List<Issue> warnings) {}
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
                case "DEVELOPMENT_SECURITY" -> new CertificationPolicy(3, 0, 1);
                case "NORMATIVE_TESTING" -> new CertificationPolicy(2, 0, 1);
                case "AGILE" -> new CertificationPolicy(null, null, null);
                case "ONE" -> new CertificationPolicy(null, null, null);
                case "JIRA" -> new CertificationPolicy(null, null, null);
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
            boolean appliesOne, boolean appliesAgile, boolean appliesJira, Long version) {}
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
        ImportedStudent withName(String value) {
            String cleaned = value == null ? null : value.trim().replaceAll("\\s+", " ");
            String[] parts = cleaned == null || cleaned.isBlank() ? new String[0] : cleaned.split("\\s+");
            String nextFirst = "";
            String nextLast = "";
            if (parts.length == 1) {
                nextFirst = parts[0];
            } else if (parts.length > 1) {
                int firstCount = parts.length >= 4 ? 2 : 1;
                nextFirst = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, firstCount));
                nextLast = String.join(" ", java.util.Arrays.copyOfRange(parts, firstCount, parts.length));
            }
            String nextNormalizedName = cleaned == null ? null : StudentExperienceService.normalizeKey(cleaned);
            String nextMatchKey = normalizedEmail != null ? "EMAIL:" + normalizedEmail
                    : nextNormalizedName == null || nextNormalizedName.isBlank() ? null : "NAME:" + nextNormalizedName;
            return new ImportedStudent(rowKey, rowNumber, cleaned, nextFirst, nextLast, nextNormalizedName,
                    email, normalizedEmail, nextMatchKey, profileName, profilePublicId, admissionDate,
                    primaryTechnology, technologicalProfileName, technologicalProfilePublicId,
                    certificationLevel, certifications, currentTechnologies, languages, knownTechnologies,
                    warnings, hasBlockingErrors);
        }
        ImportedStudent withCertificationsAndWarnings(Map<String, CertificationData> nextCertifications,
                List<String> nextWarnings) {
            return withCertificationsAndWarnings(nextCertifications, nextWarnings, hasBlockingErrors);
        }

        ImportedStudent withCertificationsAndWarnings(Map<String, CertificationData> nextCertifications,
                List<String> nextWarnings, boolean nextHasBlockingErrors) {
            return new ImportedStudent(rowKey, rowNumber, fullName, firstName, lastName, normalizedName,
                    email, normalizedEmail, matchKey, profileName, profilePublicId, admissionDate,
                    primaryTechnology, technologicalProfileName, technologicalProfilePublicId,
                    certificationLevel, nextCertifications, currentTechnologies, languages,
                    knownTechnologies, nextWarnings, nextHasBlockingErrors);
        }
    }
    private record CertificationData(String type, boolean applies, String certificationStatus, String excelCertificationStatus,
            String examStatus, String internalExamStatus, LocalDate applicationDate, LocalDate lastApprovedApplicationDate,
            BigDecimal score10, Integer attempt,
            LocalDate deadline, boolean explicitDeadline, LocalDate expiration, Boolean approved, String validityStatus,
            String trackingStatus, String processType, LocalDate referenceDate, String resultSource) {}
    private record Match(ImportedStudent imported, ExistingStudent existing) {}
    private record ConflictDecisionKey(Long studentId, String certificationType,
            String conflictCode, String fingerprint) {}
    private record PendingImport(String token, Long actorId, Long organizationId, String digest,
            java.time.Instant expiresAt, Organization organization, List<ImportedStudent> imported,
            Map<String, Match> matches, Set<String> newRowKeys,
            Map<String, Set<String>> allowedChangeFields, Set<String> possibleLowPublicIds,
            List<Issue> errors, List<Issue> warnings, Map<String, ConflictPreview> conflicts,
            Map<String, String> conflictFingerprints,
            Map<String, String> automaticConflictResolutions) {}
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
    public record ConflictAction(String value, String label, String description) {}
    public record ConflictPreview(String id, String rowKey, int row, String collaborator, String code,
            String groupKey, String title, String field, String certificationType, String certification,
            String excelValue, String currentValue, String calculatedValue, String reason,
            List<ConflictAction> actions, String resolvedAction, boolean reusedDecision) {
        ConflictPreview withResolution(String action, boolean reused) {
            return new ConflictPreview(id, rowKey, row, collaborator, code, groupKey, title, field,
                    certificationType, certification, excelValue, currentValue, calculatedValue,
                    reason, actions, action, reused);
        }
    }
    public record Preview(String token, String fileName, String sheetName, String organizationName,
            String organizationCode, int totalRows, List<NewStudentPreview> newStudents,
            List<ChangedStudentPreview> changedStudents, List<PossibleLowPreview> possibleLows,
            List<ConflictPreview> conflicts, List<Issue> warnings, List<Issue> errors, String notice) {}
    public record NewSelection(String rowKey, String email, boolean selected) {}
    public record ChangeSelection(String studentPublicId, Set<String> fields) {
        public ChangeSelection { fields = fields == null ? Set.of() : Set.copyOf(fields); }
    }
    public record LowSelection(String studentPublicId, String action) {}
    public record ConflictResolution(String conflictId, String action) {}
    public record ApplyCommand(String token, List<NewSelection> newStudents,
            List<ChangeSelection> changedStudents, List<LowSelection> possibleLows,
            List<ConflictResolution> conflicts) {}
    public record Credential(String organization, String organizationCode, String collaborator,
            String email, String studentCode, String temporaryPassword) {}
    public record ApplyResult(int created, int updated, int possibleLowsProcessed,
            List<Issue> errors, List<Credential> credentials, String credentialsNotice) {}
}
