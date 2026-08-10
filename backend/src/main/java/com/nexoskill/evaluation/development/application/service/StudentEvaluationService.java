package com.nexoskill.evaluation.development.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.AssignEvaluationCommand;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationAnswerCommand;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationAnswerReview;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationAttempt;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationAttemptQuestion;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationCard;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationSubmission;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.QuestionOption;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.ActivityItem;
import com.nexoskill.evaluation.development.infrastructure.persistence.StudentStudyQuestionRepository;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionOptionJpaEntity;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentEvaluationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final StudentStudyQuestionRepository questions;
    private final ObjectMapper json;
    private final Clock clock;

    public StudentEvaluationService(NamedParameterJdbcTemplate jdbc, StudentStudyQuestionRepository questions,
            ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EvaluationCard> list(AuthenticatedStudent student) {
        return jdbc.query("""
                SELECT assignment.PUBLIC_ID ASSIGNMENT_PUBLIC_ID, form.PUBLIC_ID FORM_PUBLIC_ID,
                       form.TITLE, form.DESCRIPTION, assignment.STATUS, assignment.DUE_AT,
                       form.DURATION_MINUTES, form.PASSING_SCORE, form.MAX_ATTEMPTS,
                       (SELECT COUNT(*) FROM STUDENT_EVALUATION_ATTEMPT attempt_count
                         WHERE attempt_count.FORM_ASSIGNMENT_ID = assignment.FORM_ASSIGNMENT_ID
                           AND attempt_count.STATUS IN ('COMPLETED','PENDING_REVIEW','EXPIRED')) ATTEMPTS_USED,
                       (SELECT COUNT(*) FROM FORM_QUESTION form_question
                           JOIN FORM_SECTION section_value ON section_value.SECTION_ID = form_question.SECTION_ID
                          WHERE section_value.FORM_ID = form.FORM_ID)
                       + NVL((SELECT SUM(pool.QUESTION_COUNT) FROM FORM_QUESTION_POOL pool
                           JOIN FORM_SECTION section_value ON section_value.SECTION_ID = pool.SECTION_ID
                          WHERE section_value.FORM_ID = form.FORM_ID), 0) QUESTION_COUNT,
                       (SELECT MAX(active_attempt.PUBLIC_ID) KEEP (DENSE_RANK FIRST ORDER BY active_attempt.STARTED_AT DESC)
                          FROM STUDENT_EVALUATION_ATTEMPT active_attempt
                         WHERE active_attempt.FORM_ASSIGNMENT_ID = assignment.FORM_ASSIGNMENT_ID
                           AND active_attempt.STATUS = 'IN_PROGRESS') ACTIVE_ATTEMPT_PUBLIC_ID,
                       form.ALLOW_SAVE_RESUME,
                       form.ACCEPT_RESPONSES, form.STARTS_AT, form.ENDS_AT
                  FROM STUDENT_FORM_ASSIGNMENT assignment
                  JOIN EVALUATION_FORM form ON form.FORM_ID = assignment.FORM_ID
                 WHERE assignment.STUDENT_ID = :studentId
                   AND assignment.ORGANIZATION_ID = :organizationId
                   AND assignment.STATUS <> 'CANCELLED'
                 ORDER BY CASE assignment.STATUS WHEN 'IN_PROGRESS' THEN 1 WHEN 'ASSIGNED' THEN 2 ELSE 3 END,
                          NVL(assignment.DUE_AT, TIMESTAMP '9999-12-31 00:00:00'), assignment.ASSIGNED_AT DESC
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> {
                    String active = rs.getString("ACTIVE_ATTEMPT_PUBLIC_ID");
                    boolean availableNow = rs.getInt("ACCEPT_RESPONSES") == 1
                            && windowOpen(timestampInstant(rs.getTimestamp("STARTS_AT")), timestampInstant(rs.getTimestamp("ENDS_AT")));
                    int used = rs.getInt("ATTEMPTS_USED");
                    Integer max = nullableInt(rs, "MAX_ATTEMPTS");
                    boolean remaining = max == null || used < max;
                    return new EvaluationCard(rs.getString("ASSIGNMENT_PUBLIC_ID"), rs.getString("FORM_PUBLIC_ID"),
                            rs.getString("TITLE"), rs.getString("DESCRIPTION"), rs.getString("STATUS"),
                            rs.getInt("QUESTION_COUNT"), nullableInt(rs, "DURATION_MINUTES"),
                            rs.getBigDecimal("PASSING_SCORE"), max, used, timestampOffset(rs.getTimestamp("DUE_AT")),
                            active == null && availableNow && remaining && !"COMPLETED".equals(rs.getString("STATUS")),
                            active != null && rs.getInt("ALLOW_SAVE_RESUME") == 1, active);
                });
    }

    @Transactional(readOnly = true)
    public List<FormOption> assignableForms(TenantContext tenant, String studentPublicId) {
        StudentTarget target = resolveStudent(tenant, studentPublicId);
        return jdbc.query("""
                SELECT form.PUBLIC_ID, form.TITLE, form.DESCRIPTION
                  FROM EVALUATION_FORM form
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID = :organizationId
                 WHERE form.STATUS = 'ACTIVE'
                   AND form.MODE_CODE = 'ASSESSMENT'
                   AND form.ACCEPT_RESPONSES = 1
                   AND (
                        (form.CONTENT_SCOPE = 'ORGANIZATION' AND form.OWNER_ORGANIZATION_ID = :organizationId)
                        OR (
                            form.CONTENT_SCOPE = 'GLOBAL'
                            AND (
                                organization_value.CONTENT_MODE = 'GLOBAL_CATALOG'
                                OR EXISTS (
                                    SELECT 1 FROM ORGANIZATION_GLOBAL_CONTENT_GRANT grant_value
                                     WHERE grant_value.ORGANIZATION_ID = :organizationId
                                       AND grant_value.CONTENT_TYPE = 'FORM'
                                       AND grant_value.GLOBAL_CONTENT_ID = form.FORM_ID
                                       AND grant_value.STATUS = 'ACTIVE'
                                       AND grant_value.DISTRIBUTION_MODE = 'GLOBAL_REFERENCE'
                                       AND (grant_value.AVAILABLE_FROM IS NULL OR grant_value.AVAILABLE_FROM <= SYSTIMESTAMP)
                                       AND (grant_value.EXPIRES_AT IS NULL OR grant_value.EXPIRES_AT > SYSTIMESTAMP)
                                )
                            )
                            AND (
                                NOT EXISTS (SELECT 1 FROM GLOBAL_CONTENT_VERSION version_value
                                             WHERE version_value.CONTENT_TYPE = 'FORM' AND version_value.CONTENT_ID = form.FORM_ID)
                                OR EXISTS (SELECT 1 FROM GLOBAL_CONTENT_VERSION version_value
                                            WHERE version_value.CONTENT_TYPE = 'FORM' AND version_value.CONTENT_ID = form.FORM_ID
                                              AND version_value.EDITORIAL_STATUS = 'PUBLISHED')
                            )
                        )
                   )
                   AND NOT EXISTS (
                        SELECT 1 FROM STUDENT_FORM_ASSIGNMENT assignment
                         WHERE assignment.STUDENT_ID = :studentId
                           AND assignment.FORM_ID = form.FORM_ID
                           AND assignment.STATUS IN ('ASSIGNED','IN_PROGRESS')
                   )
                 ORDER BY UPPER(form.TITLE), form.PUBLIC_ID
                """, new MapSqlParameterSource()
                        .addValue("organizationId", target.organizationId())
                        .addValue("studentId", target.studentId()),
                (rs, rowNum) -> new FormOption(rs.getString("PUBLIC_ID"), rs.getString("TITLE"), rs.getString("DESCRIPTION")));
    }

    @Transactional
    public EvaluationCard assign(TenantContext tenant, AuthenticatedUser actor, String studentPublicId,
            AssignEvaluationCommand command) {
        if (actor == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesión nuevamente.");
        StudentTarget target = resolveStudent(tenant, studentPublicId);
        String formPublicId = canonical(command == null ? null : command.formPublicId());
        FormRow form = requireAssignableForm(target.organizationId(), formPublicId);
        OffsetDateTime dueAt = command == null ? null : command.dueAt();
        if (dueAt != null && dueAt.isBefore(OffsetDateTime.now(clock))) {
            throw new BusinessException("EVALUATION_DUE_DATE_INVALID", "La fecha límite de la evaluación debe ser futura.");
        }
        String publicId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO STUDENT_FORM_ASSIGNMENT
                        (PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, FORM_ID, STATUS, DUE_AT,
                         ASSIGNED_BY, ASSIGNED_AT, UPDATED_AT, VERSION_NO)
                    VALUES (:publicId, :studentId, :organizationId, :formId, 'ASSIGNED', :dueAt,
                            :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0)
                    """, new MapSqlParameterSource()
                            .addValue("publicId", publicId)
                            .addValue("studentId", target.studentId())
                            .addValue("organizationId", target.organizationId())
                            .addValue("formId", form.id())
                            .addValue("dueAt", dueAt)
                            .addValue("actorId", actor.internalId()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("EVALUATION_ALREADY_ASSIGNED", "La evaluación ya está asignada y pendiente para este colaborador.");
        }
        return list(target.asAuthenticatedStudent()).stream()
                .filter(item -> item.assignmentPublicId().equals(publicId)).findFirst()
                .orElseThrow(() -> new BusinessException("EVALUATION_ASSIGNMENT_NOT_FOUND", "No fue posible consultar la evaluación asignada."));
    }

    @Transactional
    public EvaluationAttempt start(AuthenticatedStudent student, String assignmentPublicId) {
        AssignmentRow assignment = requireAssignment(student, assignmentPublicId, true);
        if ("COMPLETED".equals(assignment.status())) {
            throw new BusinessException("EVALUATION_ALREADY_COMPLETED", "Esta evaluación ya fue completada.");
        }
        FormRow form = requireRunnableForm(student.organizationId(), assignment.formId());
        if (assignment.dueAt() != null && !assignment.dueAt().isAfter(clock.instant())) {
            throw new BusinessException("EVALUATION_DUE_DATE_PASSED", "La fecha límite de esta evaluación ya terminó.");
        }
        List<AttemptRow> active = activeAttempts(assignment.id(), student);
        if (!active.isEmpty()) {
            AttemptRow attempt = active.getFirst();
            if (form.oneActiveAttempt()) return attempt(student, attempt.publicId());
        }
        int used = completedAttemptCount(assignment.id(), student);
        if (form.maxAttempts() != null && used >= form.maxAttempts()) {
            throw new BusinessException("EVALUATION_ATTEMPTS_EXHAUSTED", "Ya utilizaste el máximo de intentos configurado para esta evaluación.");
        }
        int attemptNumber = used + active.size() + 1;
        List<QuestionSelection> selection = resolveFormQuestions(form, student.organizationId());
        if (selection.isEmpty()) {
            throw new BusinessException("EVALUATION_WITHOUT_QUESTIONS", "La evaluación no tiene preguntas disponibles para tu organización.");
        }
        if (form.randomizeQuestions()) {
            Collections.shuffle(selection, new Random(UUID.randomUUID().getMostSignificantBits()));
        }
        BigDecimal totalPoints = selection.stream().map(QuestionSelection::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        String attemptPublicId = UUID.randomUUID().toString();
        Instant expiresAt = form.durationMinutes() == null ? null : clock.instant().plusSeconds(form.durationMinutes() * 60L);
        jdbc.update("""
                INSERT INTO STUDENT_EVALUATION_ATTEMPT
                    (PUBLIC_ID, FORM_ASSIGNMENT_ID, STUDENT_ID, ORGANIZATION_ID, FORM_ID, ATTEMPT_NUMBER,
                     STATUS, STARTED_AT, EXPIRES_AT, TOTAL_POINTS, UPDATED_AT, VERSION_NO)
                VALUES (:publicId, :assignmentId, :studentId, :organizationId, :formId, :attemptNumber,
                        'IN_PROGRESS', SYSTIMESTAMP, :expiresAt, :totalPoints, SYSTIMESTAMP, 0)
                """, new MapSqlParameterSource()
                        .addValue("publicId", attemptPublicId).addValue("assignmentId", assignment.id())
                        .addValue("studentId", student.internalId()).addValue("organizationId", student.organizationId())
                        .addValue("formId", form.id()).addValue("attemptNumber", attemptNumber)
                        .addValue("expiresAt", expiresAt == null ? null : Timestamp.from(expiresAt))
                        .addValue("totalPoints", totalPoints));
        Long attemptId = jdbc.queryForObject("SELECT EVALUATION_ATTEMPT_ID FROM STUDENT_EVALUATION_ATTEMPT WHERE PUBLIC_ID = :publicId",
                Map.of("publicId", attemptPublicId), Long.class);
        int order = 1;
        for (QuestionSelection selected : selection) {
            jdbc.update("""
                    INSERT INTO STUDENT_EVALUATION_QUESTION
                        (PUBLIC_ID, EVALUATION_ATTEMPT_ID, QUESTION_ID, QUESTION_ORDER, POINTS)
                    VALUES (:publicId, :attemptId, :questionId, :questionOrder, :points)
                    """, new MapSqlParameterSource().addValue("publicId", UUID.randomUUID().toString())
                            .addValue("attemptId", attemptId).addValue("questionId", selected.questionId())
                            .addValue("questionOrder", order++).addValue("points", selected.points()));
        }
        jdbc.update("UPDATE STUDENT_FORM_ASSIGNMENT SET STATUS = 'IN_PROGRESS', UPDATED_AT = SYSTIMESTAMP, VERSION_NO = VERSION_NO + 1 WHERE FORM_ASSIGNMENT_ID = :id",
                Map.of("id", assignment.id()));
        return attempt(student, attemptPublicId);
    }

    @Transactional
    public EvaluationAttempt attempt(AuthenticatedStudent student, String attemptPublicId) {
        AttemptRow row = requireAttempt(student, attemptPublicId, true);
        if ("IN_PROGRESS".equals(row.status()) && row.expiresAt() != null && !row.expiresAt().isAfter(clock.instant())) {
            jdbc.update("UPDATE STUDENT_EVALUATION_ATTEMPT SET STATUS='EXPIRED', UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1 WHERE EVALUATION_ATTEMPT_ID=:id",
                    Map.of("id", row.id()));
            row = requireAttempt(student, attemptPublicId, false);
        }
        return toAttempt(student, row);
    }

    @Transactional
    public EvaluationAttempt answer(AuthenticatedStudent student, String attemptPublicId,
            String attemptQuestionPublicId, EvaluationAnswerCommand command) {
        AttemptRow attempt = requireAttempt(student, attemptPublicId, true);
        assertInProgress(attempt);
        AttemptQuestionRow row = requireAttemptQuestion(attempt.id(), attemptQuestionPublicId);
        if (row.answered()) throw new BusinessException("EVALUATION_QUESTION_ALREADY_ANSWERED", "Esta pregunta ya tiene una respuesta guardada.");
        QuestionJpaEntity question = questions.findById(row.questionId())
                .orElseThrow(() -> new BusinessException("EVALUATION_QUESTION_NOT_FOUND", "La pregunta ya no está disponible."));
        ScoredAnswer scored = score(question, command == null ? new EvaluationAnswerCommand(List.of(), Map.of(), null) : command, row.points());
        jdbc.update("""
                INSERT INTO STUDENT_EVALUATION_ANSWER
                    (PUBLIC_ID, ATTEMPT_QUESTION_ID, SELECTED_OPTION_IDS, MATCHING_PAIRS_JSON, TEXT_ANSWER,
                     AUTO_CORRECT, EARNED_POINTS, ANSWERED_AT)
                VALUES (:publicId, :attemptQuestionId, :selectedIds, :matchingPairs, :textAnswer,
                        :autoCorrect, :earnedPoints, SYSTIMESTAMP)
                """, new MapSqlParameterSource().addValue("publicId", UUID.randomUUID().toString())
                        .addValue("attemptQuestionId", row.id()).addValue("selectedIds", join(scored.selectedIds()))
                        .addValue("matchingPairs", writeJson(scored.matchingPairs())).addValue("textAnswer", scored.textAnswer())
                        .addValue("autoCorrect", scored.correct() == null ? null : (scored.correct() ? 1 : 0))
                        .addValue("earnedPoints", scored.earnedPoints()));
        jdbc.update("UPDATE STUDENT_EVALUATION_ATTEMPT SET UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1 WHERE EVALUATION_ATTEMPT_ID=:id",
                Map.of("id", attempt.id()));
        return toAttempt(student, requireAttempt(student, attemptPublicId, false));
    }

    @Transactional
    public EvaluationSubmission submit(AuthenticatedStudent student, String attemptPublicId) {
        AttemptRow attempt = requireAttempt(student, attemptPublicId, true);
        assertInProgress(attempt);
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM STUDENT_EVALUATION_QUESTION WHERE EVALUATION_ATTEMPT_ID=:id",
                Map.of("id", attempt.id()), Integer.class);
        int answered = jdbc.queryForObject("""
                SELECT COUNT(*) FROM STUDENT_EVALUATION_ANSWER answer_value
                 JOIN STUDENT_EVALUATION_QUESTION question_value ON question_value.ATTEMPT_QUESTION_ID=answer_value.ATTEMPT_QUESTION_ID
                WHERE question_value.EVALUATION_ATTEMPT_ID=:id
                """, Map.of("id", attempt.id()), Integer.class);
        if (answered < total) throw new BusinessException("EVALUATION_INCOMPLETE", "Responde todas las preguntas antes de enviar la evaluación.");
        Integer manual = jdbc.queryForObject("""
                SELECT COUNT(*) FROM STUDENT_EVALUATION_ANSWER answer_value
                 JOIN STUDENT_EVALUATION_QUESTION question_value ON question_value.ATTEMPT_QUESTION_ID=answer_value.ATTEMPT_QUESTION_ID
                WHERE question_value.EVALUATION_ATTEMPT_ID=:id AND answer_value.AUTO_CORRECT IS NULL
                """, Map.of("id", attempt.id()), Integer.class);
        if (manual != null && manual > 0) {
            jdbc.update("""
                    UPDATE STUDENT_EVALUATION_ATTEMPT
                       SET STATUS='PENDING_REVIEW', SUBMITTED_AT=SYSTIMESTAMP, EARNED_POINTS=NULL, SCORE=NULL, PASSED=NULL,
                           UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                     WHERE EVALUATION_ATTEMPT_ID=:id
                    """, Map.of("id", attempt.id()));
            jdbc.update("UPDATE STUDENT_FORM_ASSIGNMENT SET STATUS='IN_PROGRESS', UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1 WHERE FORM_ASSIGNMENT_ID=:id",
                    Map.of("id", attempt.assignmentId()));
            String message = hasText(attempt.thankYouMessage())
                    ? attempt.thankYouMessage().trim() + " Tu resultado está pendiente de revisión."
                    : "Tu evaluación fue enviada y tiene respuestas pendientes de revisión.";
            return new EvaluationSubmission(attempt.publicId(), "PENDING_REVIEW", null, null, answered, total,
                    message, List.of());
        }
        BigDecimal earned = jdbc.queryForObject("""
                SELECT NVL(SUM(answer_value.EARNED_POINTS),0)
                  FROM STUDENT_EVALUATION_ANSWER answer_value
                  JOIN STUDENT_EVALUATION_QUESTION question_value ON question_value.ATTEMPT_QUESTION_ID=answer_value.ATTEMPT_QUESTION_ID
                 WHERE question_value.EVALUATION_ATTEMPT_ID=:id
                """, Map.of("id", attempt.id()), BigDecimal.class);
        BigDecimal score = attempt.totalPoints().signum() == 0 ? BigDecimal.ZERO
                : earned.multiply(BigDecimal.valueOf(100)).divide(attempt.totalPoints(), 2, RoundingMode.HALF_UP);
        boolean passed = score.compareTo(attempt.passingScore()) >= 0;
        jdbc.update("""
                UPDATE STUDENT_EVALUATION_ATTEMPT
                   SET STATUS='COMPLETED', SUBMITTED_AT=SYSTIMESTAMP, EARNED_POINTS=:earned, SCORE=:score, PASSED=:passed,
                       UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                 WHERE EVALUATION_ATTEMPT_ID=:id
                """, new MapSqlParameterSource().addValue("earned", earned).addValue("score", score)
                        .addValue("passed", passed ? 1 : 0).addValue("id", attempt.id()));
        FormRow form = requireRunnableForm(student.organizationId(), attempt.formId());
        int used = completedAttemptCount(attempt.assignmentId(), student);
        boolean exhausted = form.maxAttempts() != null && used >= form.maxAttempts();
        boolean finished = passed || exhausted || !form.retryUntilPassed();
        jdbc.update("""
                UPDATE STUDENT_FORM_ASSIGNMENT
                   SET STATUS=:status, COMPLETED_AT=CASE WHEN :completed=1 THEN SYSTIMESTAMP ELSE NULL END,
                       UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1
                 WHERE FORM_ASSIGNMENT_ID=:id
                """, new MapSqlParameterSource().addValue("status", finished ? "COMPLETED" : "ASSIGNED")
                        .addValue("completed", finished ? 1 : 0).addValue("id", attempt.assignmentId()));
        BigDecimal visibleScore = attempt.showResults() ? score : null;
        Boolean visiblePassed = attempt.showResults() ? passed : null;
        String message = completionMessage(attempt, passed);
        List<EvaluationAnswerReview> review = attempt.showCorrectAnswers() ? answerReview(attempt) : List.of();
        return new EvaluationSubmission(attempt.publicId(), "COMPLETED", visibleScore, visiblePassed, answered, total,
                message, review);
    }

    private String completionMessage(AttemptRow attempt, boolean passed) {
        if (hasText(attempt.thankYouMessage())) return attempt.thankYouMessage().trim();
        if (!attempt.showResults()) return "Tu evaluación fue enviada correctamente.";
        return passed ? "Evaluación completada. Alcanzaste el puntaje mínimo."
                : "Evaluación completada. Revisa tu resultado y prepárate antes del siguiente intento.";
    }

    private List<EvaluationAnswerReview> answerReview(AttemptRow attempt) {
        List<ReviewRow> rows = jdbc.query("""
                SELECT question_value.PUBLIC_ID ATTEMPT_QUESTION_PUBLIC_ID, question_value.QUESTION_ID, answer_value.AUTO_CORRECT
                  FROM STUDENT_EVALUATION_QUESTION question_value
                  JOIN STUDENT_EVALUATION_ANSWER answer_value ON answer_value.ATTEMPT_QUESTION_ID=question_value.ATTEMPT_QUESTION_ID
                 WHERE question_value.EVALUATION_ATTEMPT_ID=:attemptId
                 ORDER BY question_value.QUESTION_ORDER
                """, Map.of("attemptId", attempt.id()), (rs, rowNum) -> new ReviewRow(
                        rs.getString("ATTEMPT_QUESTION_PUBLIC_ID"), rs.getLong("QUESTION_ID"),
                        nullableBoolean(rs, "AUTO_CORRECT")));
        Map<Long, QuestionJpaEntity> byId = questions.findAllByIdIn(rows.stream().map(ReviewRow::questionId).toList())
                .stream().collect(Collectors.toMap(QuestionJpaEntity::getId, Function.identity()));
        List<EvaluationAnswerReview> result = new ArrayList<>();
        for (ReviewRow row : rows) {
            QuestionJpaEntity question = byId.get(row.questionId());
            if (question == null || row.correct() == null) continue;
            String type = question.getType().getCode();
            List<String> correctOptionIds = List.of();
            Map<String, String> correctPairs = Map.of();
            List<String> acceptedText = List.of();
            if ("MATCHING".equals(type)) {
                LinkedHashMap<String, String> pairs = new LinkedHashMap<>();
                question.getOptions().forEach(option -> pairs.put(option.getPublicId(), option.getPublicId()));
                correctPairs = Map.copyOf(pairs);
            } else if ("OPEN_TEXT".equals(type)) {
                acceptedText = acceptedAnswers(question.getAcceptedAnswersJson());
            } else {
                correctOptionIds = question.getOptions().stream().filter(QuestionOptionJpaEntity::isCorrect)
                        .map(QuestionOptionJpaEntity::getPublicId).toList();
            }
            result.add(new EvaluationAnswerReview(row.attemptQuestionPublicId(), row.correct(), correctOptionIds,
                    correctPairs, acceptedText));
        }
        return List.copyOf(result);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> recentActivity(AuthenticatedStudent student, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return jdbc.query("""
                SELECT OCCURRED_AT, EVENT_TYPE, TITLE, DESCRIPTION
                  FROM (
                        SELECT activity_value.*, ROW_NUMBER() OVER (ORDER BY activity_value.OCCURRED_AT DESC) RN
                          FROM (
                                SELECT attempt.SUBMITTED_AT OCCURRED_AT, 'EVALUATION_COMPLETED' EVENT_TYPE,
                                       'Completaste ' || form.TITLE TITLE,
                                       CASE WHEN attempt.SCORE IS NULL THEN 'Resultado pendiente de revisión'
                                            ELSE 'Resultado: ' || TO_CHAR(attempt.SCORE, 'FM990D00') || '%' END DESCRIPTION
                                  FROM STUDENT_EVALUATION_ATTEMPT attempt
                                  JOIN EVALUATION_FORM form ON form.FORM_ID=attempt.FORM_ID
                                 WHERE attempt.STUDENT_ID=:studentId AND attempt.ORGANIZATION_ID=:organizationId
                                   AND attempt.STATUS IN ('COMPLETED','PENDING_REVIEW')
                                   AND attempt.SUBMITTED_AT IS NOT NULL
                               ) activity_value
                       ) ranked_value
                 WHERE RN <= :limit
                 ORDER BY OCCURRED_AT DESC
                """, new MapSqlParameterSource().addValue("studentId", student.internalId())
                        .addValue("organizationId", student.organizationId()).addValue("limit", safeLimit),
                (rs, rowNum) -> new ActivityItem(rs.getString("EVENT_TYPE"), rs.getTimestamp("OCCURRED_AT").toInstant(),
                        rs.getString("TITLE"), rs.getString("DESCRIPTION")));
    }

    private EvaluationAttempt toAttempt(AuthenticatedStudent student, AttemptRow attempt) {
        List<AttemptQuestionRow> rows = jdbc.query("""
                SELECT question_value.ATTEMPT_QUESTION_ID, question_value.PUBLIC_ID, question_value.QUESTION_ID,
                       question_value.QUESTION_ORDER, question_value.POINTS,
                       answer_value.SELECTED_OPTION_IDS, answer_value.MATCHING_PAIRS_JSON, answer_value.TEXT_ANSWER,
                       CASE WHEN answer_value.EVALUATION_ANSWER_ID IS NULL THEN 0 ELSE 1 END ANSWERED
                  FROM STUDENT_EVALUATION_QUESTION question_value
                  LEFT JOIN STUDENT_EVALUATION_ANSWER answer_value ON answer_value.ATTEMPT_QUESTION_ID=question_value.ATTEMPT_QUESTION_ID
                 WHERE question_value.EVALUATION_ATTEMPT_ID=:attemptId
                 ORDER BY question_value.QUESTION_ORDER
                """, Map.of("attemptId", attempt.id()), (rs, rowNum) -> new AttemptQuestionRow(
                        rs.getLong("ATTEMPT_QUESTION_ID"), rs.getString("PUBLIC_ID"), rs.getLong("QUESTION_ID"),
                        rs.getInt("QUESTION_ORDER"), rs.getBigDecimal("POINTS"), rs.getInt("ANSWERED") == 1,
                        split(rs.getString("SELECTED_OPTION_IDS")), readPairs(rs.getString("MATCHING_PAIRS_JSON")),
                        rs.getString("TEXT_ANSWER")));
        Map<Long, QuestionJpaEntity> byId = questions.findAllByIdIn(rows.stream().map(AttemptQuestionRow::questionId).toList())
                .stream().collect(Collectors.toMap(QuestionJpaEntity::getId, Function.identity()));
        List<EvaluationAttemptQuestion> items = new ArrayList<>();
        for (AttemptQuestionRow row : rows) {
            QuestionJpaEntity question = byId.get(row.questionId());
            if (question == null) throw new BusinessException("EVALUATION_QUESTION_NOT_FOUND", "Una pregunta de la evaluación ya no está disponible.");
            items.add(toQuestion(attempt, row, question, rows.size()));
        }
        EvaluationAttemptQuestion current = "IN_PROGRESS".equals(attempt.status())
                ? items.stream().filter(item -> !item.answered()).findFirst().orElse(null) : null;
        BigDecimal visibleScore = attempt.showResults() ? attempt.score() : null;
        Boolean visiblePassed = attempt.showResults() ? attempt.passed() : null;
        return new EvaluationAttempt(attempt.publicId(), attempt.assignmentPublicId(), attempt.formPublicId(), attempt.formTitle(),
                attempt.status(), attempt.attemptNumber(), attempt.maxAttempts(), attempt.durationMinutes(), attempt.passingScore(),
                attempt.startedAt(), attempt.expiresAt(), attempt.submittedAt(), visibleScore, visiblePassed,
                attempt.showProgress(), !attempt.hideQuestionNumbers(), attempt.allowSaveResume(), current, items);
    }

    private EvaluationAttemptQuestion toQuestion(AttemptRow attempt, AttemptQuestionRow row, QuestionJpaEntity question, int total) {
        List<QuestionOptionJpaEntity> source = new ArrayList<>(question.getOptions());
        if (attempt.randomizeOptions()) Collections.shuffle(source, new Random(Objects.hash(attempt.publicId(), row.publicId())));
        List<QuestionOption> options = source.stream().map(value -> new QuestionOption(value.getPublicId(), value.getText())).toList();
        List<QuestionOption> right = List.of();
        if ("MATCHING".equals(question.getType().getCode())) {
            List<QuestionOption> mutable = source.stream().map(value -> new QuestionOption(value.getPublicId(), value.getMatchText())).toList();
            mutable = new ArrayList<>(mutable);
            Collections.shuffle(mutable, new Random(Objects.hash(row.publicId(), attempt.publicId(), "right")));
            right = List.copyOf(mutable);
        }
        return new EvaluationAttemptQuestion(row.publicId(), question.getPublicId(), row.order(), total,
                question.getType().getCode(), question.getStatement(), question.getCodeLanguage(), question.getCodeContent(),
                options, right, row.points(), row.answered(), row.selectedIds(), row.matchingPairs(), row.textAnswer());
    }

    private ScoredAnswer score(QuestionJpaEntity question, EvaluationAnswerCommand command, BigDecimal points) {
        String type = question.getType().getCode();
        if ("OPEN_TEXT".equals(type)) {
            String text = command.textAnswer() == null ? "" : command.textAnswer().trim();
            if (text.isBlank()) throw new BusinessException("EVALUATION_ANSWER_REQUIRED", "Escribe una respuesta antes de continuar.");
            if (question.isManualReview()) return new ScoredAnswer(List.of(), Map.of(), text, null, null);
            List<String> accepted = acceptedAnswers(question.getAcceptedAnswersJson());
            if (accepted.isEmpty()) return new ScoredAnswer(List.of(), Map.of(), text, null, null);
            boolean correct = accepted.stream().anyMatch(value -> question.isCaseSensitive()
                    ? value.equals(text) : value.equalsIgnoreCase(text));
            return new ScoredAnswer(List.of(), Map.of(), text, correct, correct ? points : BigDecimal.ZERO);
        }
        if ("MATCHING".equals(type)) {
            Map<String, String> pairs = command.matchingPairs();
            Set<String> valid = question.getOptions().stream().map(QuestionOptionJpaEntity::getPublicId).collect(Collectors.toSet());
            if (pairs.size() != valid.size() || !valid.containsAll(pairs.keySet()) || !valid.containsAll(pairs.values())) {
                throw new BusinessException("EVALUATION_MATCHING_INCOMPLETE", "Relaciona todas las opciones antes de continuar.");
            }
            boolean correct = pairs.entrySet().stream().allMatch(entry -> entry.getKey().equals(entry.getValue()));
            return new ScoredAnswer(List.of(), pairs, null, correct, correct ? points : BigDecimal.ZERO);
        }
        Set<String> valid = question.getOptions().stream().map(QuestionOptionJpaEntity::getPublicId).collect(Collectors.toSet());
        LinkedHashSet<String> selected = new LinkedHashSet<>(command.selectedOptionPublicIds());
        if (selected.isEmpty() || !valid.containsAll(selected)) {
            throw new BusinessException("EVALUATION_OPTION_INVALID", "Selecciona una respuesta válida antes de continuar.");
        }
        if (("SINGLE_CHOICE".equals(type) || "TRUE_FALSE".equals(type)) && selected.size() != 1) {
            throw new BusinessException("EVALUATION_SINGLE_ANSWER_REQUIRED", "Selecciona una sola respuesta antes de continuar.");
        }
        Set<String> correctIds = question.getOptions().stream().filter(QuestionOptionJpaEntity::isCorrect)
                .map(QuestionOptionJpaEntity::getPublicId).collect(Collectors.toSet());
        boolean correct = selected.equals(correctIds);
        return new ScoredAnswer(List.copyOf(selected), Map.of(), null, correct, correct ? points : BigDecimal.ZERO);
    }

    private List<QuestionSelection> resolveFormQuestions(FormRow form, Long organizationId) {
        List<QuestionSelection> selected = new ArrayList<>();
        if ("MANUAL".equals(form.contentMode())) {
            List<QuestionSelection> configured = jdbc.query("""
                    SELECT question_value.QUESTION_ID, form_question.POINTS
                      FROM FORM_SECTION section_value
                      JOIN FORM_QUESTION form_question ON form_question.SECTION_ID=section_value.SECTION_ID
                      JOIN QUESTION question_value ON question_value.QUESTION_ID=form_question.QUESTION_ID
                     WHERE section_value.FORM_ID=:formId
                       AND question_value.STATUS='ACTIVE'
                       AND """ + readableQuestionPredicate("question_value") + """
                     ORDER BY section_value.SECTION_ORDER, form_question.QUESTION_ORDER
                    """, Map.of("formId", form.id(), "organizationId", organizationId),
                    (rs, rowNum) -> new QuestionSelection(rs.getLong("QUESTION_ID"), rs.getBigDecimal("POINTS")));
            int expected = jdbc.queryForObject("SELECT COUNT(*) FROM FORM_QUESTION fq JOIN FORM_SECTION fs ON fs.SECTION_ID=fq.SECTION_ID WHERE fs.FORM_ID=:id",
                    Map.of("id", form.id()), Integer.class);
            if (configured.size() != expected) {
                throw new BusinessException("EVALUATION_CONTENT_UNAVAILABLE", "La evaluación contiene preguntas que no están disponibles para tu organización.");
            }
            selected.addAll(configured);
        } else {
            List<PoolRow> pools = jdbc.query("""
                    SELECT pool.SOURCE_TYPE, pool.CATEGORY_ID, pool.COLLECTION_ID,
                           pool.QUESTION_COUNT, pool.DIFFICULTY_CODE
                      FROM FORM_SECTION section_value
                      JOIN FORM_QUESTION_POOL pool ON pool.SECTION_ID=section_value.SECTION_ID
                     WHERE section_value.FORM_ID=:formId
                     ORDER BY section_value.SECTION_ORDER, pool.POOL_ORDER
                    """, Map.of("formId", form.id()), (rs, rowNum) -> new PoolRow(rs.getString("SOURCE_TYPE"),
                            nullableLong(rs, "CATEGORY_ID"), nullableLong(rs, "COLLECTION_ID"),
                            rs.getInt("QUESTION_COUNT"), rs.getString("DIFFICULTY_CODE")));
            for (PoolRow pool : pools) {
                List<Long> ids = randomPoolQuestionIds(organizationId, pool, selected.stream().map(QuestionSelection::questionId).toList());
                if (ids.size() < pool.count()) {
                    throw new BusinessException("EVALUATION_POOL_INSUFFICIENT", "No hay suficientes preguntas disponibles para construir esta evaluación.");
                }
                ids.forEach(id -> selected.add(new QuestionSelection(id, BigDecimal.ONE)));
            }
        }
        return selected;
    }

    private List<Long> randomPoolQuestionIds(Long organizationId, PoolRow pool, List<Long> excluded) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("organizationId", organizationId)
                .addValue("difficultyCode", pool.difficultyCode()).addValue("limit", pool.count());
        String sourcePredicate;
        if ("CATEGORY".equals(pool.sourceType()) && pool.categoryId() != null) {
            params.addValue("categoryId", pool.categoryId());
            sourcePredicate = """
                    AND EXISTS (SELECT 1 FROM QUESTION_CATEGORY_RELATION relation
                                 WHERE relation.QUESTION_ID=question_value.QUESTION_ID
                                   AND relation.CATEGORY_ID=:categoryId)
                    """;
        } else if ("COLLECTION".equals(pool.sourceType()) && pool.collectionId() != null) {
            params.addValue("collectionId", pool.collectionId());
            sourcePredicate = """
                    AND EXISTS (SELECT 1 FROM COLLECTION_QUESTION_RELATION relation
                                 WHERE relation.QUESTION_ID=question_value.QUESTION_ID
                                   AND relation.COLLECTION_ID=:collectionId)
                    """;
        } else {
            throw new BusinessException("EVALUATION_POOL_INVALID", "La evaluación contiene un pool de preguntas inválido.");
        }
        String excludedClause = "";
        if (!excluded.isEmpty()) {
            params.addValue("excluded", excluded);
            excludedClause = " AND question_value.QUESTION_ID NOT IN (:excluded) ";
        }
        return jdbc.query("""
                SELECT QUESTION_ID FROM (
                    SELECT question_value.QUESTION_ID
                      FROM QUESTION question_value
                     WHERE question_value.STATUS='ACTIVE'
                       AND """ + readableQuestionPredicate("question_value") + sourcePredicate + """
                       AND (:difficultyCode IS NULL OR question_value.DIFFICULTY_CODE=:difficultyCode)
                       """ + excludedClause + """
                     ORDER BY DBMS_RANDOM.VALUE
                ) WHERE ROWNUM <= :limit
                """, params, (rs, rowNum) -> rs.getLong("QUESTION_ID"));
    }

    private String readableQuestionPredicate(String alias) {
        return """
                ((%s.CONTENT_SCOPE='ORGANIZATION' AND %s.OWNER_ORGANIZATION_ID=:organizationId)
                 OR (%s.CONTENT_SCOPE='GLOBAL'
                     AND (NVL(%s.AVAILABILITY_MODE,'GLOBAL')='GLOBAL'
                          OR EXISTS (SELECT 1 FROM QUESTION_ORGANIZATION_AVAILABILITY availability
                                     WHERE availability.QUESTION_ID=%s.QUESTION_ID
                                       AND availability.ORGANIZATION_ID=:organizationId AND availability.STATUS='ACTIVE'))
                     AND (NOT EXISTS (SELECT 1 FROM GLOBAL_CONTENT_VERSION version_value
                                      WHERE version_value.CONTENT_TYPE='QUESTION' AND version_value.CONTENT_ID=%s.QUESTION_ID)
                          OR EXISTS (SELECT 1 FROM GLOBAL_CONTENT_VERSION version_value
                                     WHERE version_value.CONTENT_TYPE='QUESTION' AND version_value.CONTENT_ID=%s.QUESTION_ID
                                       AND version_value.EDITORIAL_STATUS='PUBLISHED'))))
                """.formatted(alias, alias, alias, alias, alias, alias, alias);
    }

    private FormRow requireAssignableForm(Long organizationId, String formPublicId) {
        List<FormRow> rows = formRows("form.PUBLIC_ID=:formPublicId", new MapSqlParameterSource()
                .addValue("organizationId", organizationId).addValue("formPublicId", formPublicId));
        if (rows.isEmpty()) throw new BusinessException("EVALUATION_FORM_NOT_AVAILABLE", "El formulario no está disponible para esta organización.");
        return rows.getFirst();
    }

    private FormRow requireRunnableForm(Long organizationId, Long formId) {
        List<FormRow> rows = formRows("form.FORM_ID=:formId", new MapSqlParameterSource()
                .addValue("organizationId", organizationId).addValue("formId", formId));
        if (rows.isEmpty()) throw new BusinessException("EVALUATION_FORM_NOT_AVAILABLE", "La evaluación ya no está disponible.");
        FormRow form = rows.getFirst();
        if (!windowOpen(form.startsAt(), form.endsAt())) throw new BusinessException("EVALUATION_WINDOW_CLOSED", "La evaluación no está disponible en este momento.");
        return form;
    }

    private List<FormRow> formRows(String selector, MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT form.FORM_ID, form.PUBLIC_ID, form.TITLE, form.DESCRIPTION, form.CONTENT_MODE,
                       form.PASSING_SCORE, form.MAX_ATTEMPTS, form.RETRY_UNTIL_PASSED, form.DURATION_MINUTES,
                       form.RANDOMIZE_QUESTIONS, form.RANDOMIZE_OPTIONS, form.SHOW_PROGRESS, form.SHOW_RESULTS,
                       form.SHOW_CORRECT_ANSWERS, form.HIDE_QUESTION_NUMBERS, form.ALLOW_SAVE_RESUME,
                       form.ONE_ACTIVE_ATTEMPT, form.THANK_YOU_MESSAGE, form.STARTS_AT, form.ENDS_AT
                  FROM EVALUATION_FORM form
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=:organizationId
                 WHERE """ + selector + """
                   AND form.STATUS='ACTIVE' AND form.MODE_CODE='ASSESSMENT' AND form.ACCEPT_RESPONSES=1
                   AND ((form.CONTENT_SCOPE='ORGANIZATION' AND form.OWNER_ORGANIZATION_ID=:organizationId)
                     OR (form.CONTENT_SCOPE='GLOBAL'
                         AND (organization_value.CONTENT_MODE='GLOBAL_CATALOG'
                           OR EXISTS (SELECT 1 FROM ORGANIZATION_GLOBAL_CONTENT_GRANT grant_value
                                      WHERE grant_value.ORGANIZATION_ID=:organizationId AND grant_value.CONTENT_TYPE='FORM'
                                        AND grant_value.GLOBAL_CONTENT_ID=form.FORM_ID AND grant_value.STATUS='ACTIVE'
                                        AND grant_value.DISTRIBUTION_MODE='GLOBAL_REFERENCE'
                                        AND (grant_value.AVAILABLE_FROM IS NULL OR grant_value.AVAILABLE_FROM<=SYSTIMESTAMP)
                                        AND (grant_value.EXPIRES_AT IS NULL OR grant_value.EXPIRES_AT>SYSTIMESTAMP)))
                         AND (NOT EXISTS (SELECT 1 FROM GLOBAL_CONTENT_VERSION version_value
                                          WHERE version_value.CONTENT_TYPE='FORM' AND version_value.CONTENT_ID=form.FORM_ID)
                           OR EXISTS (SELECT 1 FROM GLOBAL_CONTENT_VERSION version_value
                                      WHERE version_value.CONTENT_TYPE='FORM' AND version_value.CONTENT_ID=form.FORM_ID
                                        AND version_value.EDITORIAL_STATUS='PUBLISHED'))))
                """, params, (rs, rowNum) -> new FormRow(rs.getLong("FORM_ID"), rs.getString("PUBLIC_ID"),
                        rs.getString("TITLE"), rs.getString("DESCRIPTION"), rs.getString("CONTENT_MODE"),
                        rs.getBigDecimal("PASSING_SCORE"), nullableInt(rs, "MAX_ATTEMPTS"), rs.getInt("RETRY_UNTIL_PASSED") == 1,
                        nullableInt(rs, "DURATION_MINUTES"), rs.getInt("RANDOMIZE_QUESTIONS") == 1,
                        rs.getInt("RANDOMIZE_OPTIONS") == 1, rs.getInt("SHOW_PROGRESS") == 1,
                        rs.getInt("SHOW_RESULTS") == 1, rs.getInt("SHOW_CORRECT_ANSWERS") == 1,
                        rs.getInt("HIDE_QUESTION_NUMBERS") == 1, rs.getInt("ALLOW_SAVE_RESUME") == 1,
                        rs.getInt("ONE_ACTIVE_ATTEMPT") == 1, rs.getString("THANK_YOU_MESSAGE"),
                        timestampInstant(rs.getTimestamp("STARTS_AT")), timestampInstant(rs.getTimestamp("ENDS_AT"))));
    }

    private StudentTarget resolveStudent(TenantContext tenant, String studentPublicId) {
        if (tenant == null) throw new BusinessException("TENANT_NOT_RESOLVED", "No fue posible determinar el alcance de la operación.");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("publicId", canonical(studentPublicId));
        String scope;
        if (tenant.globalAdministrator() && tenant.globalScope()) {
            scope = "";
        } else if (tenant.hasOrganization()) {
            scope = " AND student.ORGANIZATION_ID=:organizationId";
            params.addValue("organizationId", tenant.organizationId());
        } else {
            throw new BusinessException("TENANT_NOT_RESOLVED", "No fue posible determinar la organización.");
        }
        List<StudentTarget> rows = jdbc.query("""
                SELECT student.STUDENT_ID, student.PUBLIC_ID, student.ORGANIZATION_ID,
                       organization_value.PUBLIC_ID ORGANIZATION_PUBLIC_ID, organization_value.ORGANIZATION_CODE,
                       organization_value.ORGANIZATION_NAME, student.STUDENT_CODE, student.EMAIL,
                       student.FIRST_NAME, student.LAST_NAME, student.DISPLAY_NAME
                  FROM STUDENT student
                  JOIN ORGANIZATION organization_value ON organization_value.ORGANIZATION_ID=student.ORGANIZATION_ID
                 WHERE student.PUBLIC_ID=:publicId AND student.STATUS<>'DELETED' AND student.RECORD_MODULE='COLLABORATOR'
                """ + scope, params, (rs, rowNum) -> new StudentTarget(rs.getLong("STUDENT_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("ORGANIZATION_ID"), rs.getString("ORGANIZATION_PUBLIC_ID"), rs.getString("ORGANIZATION_CODE"),
                        rs.getString("ORGANIZATION_NAME"), rs.getString("STUDENT_CODE"), rs.getString("EMAIL"),
                        rs.getString("FIRST_NAME"), rs.getString("LAST_NAME"), rs.getString("DISPLAY_NAME")));
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El colaborador no existe dentro de tu alcance.");
        return rows.getFirst();
    }

    private AssignmentRow requireAssignment(AuthenticatedStudent student, String publicId, boolean forUpdate) {
        List<AssignmentRow> rows = jdbc.query("""
                SELECT assignment.FORM_ASSIGNMENT_ID, assignment.PUBLIC_ID, assignment.FORM_ID, assignment.STATUS, assignment.DUE_AT
                  FROM STUDENT_FORM_ASSIGNMENT assignment
                 WHERE assignment.PUBLIC_ID=:publicId AND assignment.STUDENT_ID=:studentId AND assignment.ORGANIZATION_ID=:organizationId
                """ + (forUpdate ? " FOR UPDATE" : ""), Map.of("publicId", canonical(publicId),
                        "studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new AssignmentRow(rs.getLong("FORM_ASSIGNMENT_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("FORM_ID"), rs.getString("STATUS"), timestampInstant(rs.getTimestamp("DUE_AT"))));
        if (rows.isEmpty()) throw new BusinessException("EVALUATION_ASSIGNMENT_NOT_FOUND", "La evaluación no existe o no pertenece a tu cuenta.");
        return rows.getFirst();
    }

    private AttemptRow requireAttempt(AuthenticatedStudent student, String publicId, boolean forUpdate) {
        List<AttemptRow> rows = jdbc.query("""
                SELECT attempt.EVALUATION_ATTEMPT_ID, attempt.PUBLIC_ID, attempt.FORM_ASSIGNMENT_ID,
                       assignment.PUBLIC_ID ASSIGNMENT_PUBLIC_ID, attempt.FORM_ID, form.PUBLIC_ID FORM_PUBLIC_ID,
                       form.TITLE FORM_TITLE, attempt.ATTEMPT_NUMBER, attempt.STATUS, attempt.STARTED_AT, attempt.EXPIRES_AT,
                       attempt.SUBMITTED_AT, attempt.TOTAL_POINTS, attempt.SCORE, attempt.PASSED,
                       form.PASSING_SCORE, form.MAX_ATTEMPTS, form.DURATION_MINUTES, form.SHOW_PROGRESS,
                       form.SHOW_RESULTS, form.SHOW_CORRECT_ANSWERS, form.HIDE_QUESTION_NUMBERS,
                       form.ALLOW_SAVE_RESUME, form.RANDOMIZE_OPTIONS, form.THANK_YOU_MESSAGE
                  FROM STUDENT_EVALUATION_ATTEMPT attempt
                  JOIN STUDENT_FORM_ASSIGNMENT assignment ON assignment.FORM_ASSIGNMENT_ID=attempt.FORM_ASSIGNMENT_ID
                  JOIN EVALUATION_FORM form ON form.FORM_ID=attempt.FORM_ID
                 WHERE attempt.PUBLIC_ID=:publicId AND attempt.STUDENT_ID=:studentId AND attempt.ORGANIZATION_ID=:organizationId
                """ + (forUpdate ? " FOR UPDATE" : ""), Map.of("publicId", canonical(publicId),
                        "studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new AttemptRow(rs.getLong("EVALUATION_ATTEMPT_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("FORM_ASSIGNMENT_ID"), rs.getString("ASSIGNMENT_PUBLIC_ID"), rs.getLong("FORM_ID"),
                        rs.getString("FORM_PUBLIC_ID"), rs.getString("FORM_TITLE"), rs.getInt("ATTEMPT_NUMBER"), rs.getString("STATUS"),
                        timestampInstant(rs.getTimestamp("STARTED_AT")), timestampInstant(rs.getTimestamp("EXPIRES_AT")),
                        timestampInstant(rs.getTimestamp("SUBMITTED_AT")), rs.getBigDecimal("TOTAL_POINTS"),
                        rs.getBigDecimal("PASSING_SCORE"), nullableInt(rs, "MAX_ATTEMPTS"), nullableInt(rs, "DURATION_MINUTES"),
                        rs.getInt("SHOW_PROGRESS") == 1, rs.getInt("SHOW_RESULTS") == 1,
                        rs.getInt("SHOW_CORRECT_ANSWERS") == 1, rs.getInt("HIDE_QUESTION_NUMBERS") == 1,
                        rs.getInt("ALLOW_SAVE_RESUME") == 1, rs.getInt("RANDOMIZE_OPTIONS") == 1,
                        rs.getString("THANK_YOU_MESSAGE"), rs.getBigDecimal("SCORE"), nullableBoolean(rs, "PASSED")));
        if (rows.isEmpty()) throw new BusinessException("EVALUATION_ATTEMPT_NOT_FOUND", "El intento no existe o no pertenece a tu cuenta.");
        return rows.getFirst();
    }

    private AttemptQuestionRow requireAttemptQuestion(Long attemptId, String publicId) {
        List<AttemptQuestionRow> rows = jdbc.query("""
                SELECT question_value.ATTEMPT_QUESTION_ID, question_value.PUBLIC_ID, question_value.QUESTION_ID,
                       question_value.QUESTION_ORDER, question_value.POINTS,
                       CASE WHEN answer_value.EVALUATION_ANSWER_ID IS NULL THEN 0 ELSE 1 END ANSWERED,
                       answer_value.SELECTED_OPTION_IDS, answer_value.MATCHING_PAIRS_JSON, answer_value.TEXT_ANSWER
                  FROM STUDENT_EVALUATION_QUESTION question_value
                  LEFT JOIN STUDENT_EVALUATION_ANSWER answer_value ON answer_value.ATTEMPT_QUESTION_ID=question_value.ATTEMPT_QUESTION_ID
                 WHERE question_value.EVALUATION_ATTEMPT_ID=:attemptId AND question_value.PUBLIC_ID=:publicId
                """, Map.of("attemptId", attemptId, "publicId", canonical(publicId)),
                (rs, rowNum) -> new AttemptQuestionRow(rs.getLong("ATTEMPT_QUESTION_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("QUESTION_ID"), rs.getInt("QUESTION_ORDER"), rs.getBigDecimal("POINTS"),
                        rs.getInt("ANSWERED") == 1, split(rs.getString("SELECTED_OPTION_IDS")),
                        readPairs(rs.getString("MATCHING_PAIRS_JSON")), rs.getString("TEXT_ANSWER")));
        if (rows.isEmpty()) throw new BusinessException("EVALUATION_QUESTION_NOT_FOUND", "La pregunta no pertenece a este intento.");
        return rows.getFirst();
    }

    private List<AttemptRow> activeAttempts(Long assignmentId, AuthenticatedStudent student) {
        List<String> publicIds = jdbc.query("""
                SELECT attempt.PUBLIC_ID
                  FROM STUDENT_EVALUATION_ATTEMPT attempt
                 WHERE attempt.FORM_ASSIGNMENT_ID=:assignmentId AND attempt.STUDENT_ID=:studentId
                   AND attempt.ORGANIZATION_ID=:organizationId AND attempt.STATUS='IN_PROGRESS'
                 ORDER BY attempt.STARTED_AT DESC
                """, Map.of("assignmentId", assignmentId, "studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> rs.getString("PUBLIC_ID"));
        return publicIds.stream().map(publicId -> requireAttempt(student, publicId, false)).toList();
    }

    private int completedAttemptCount(Long assignmentId, AuthenticatedStudent student) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM STUDENT_EVALUATION_ATTEMPT
                 WHERE FORM_ASSIGNMENT_ID=:assignmentId AND STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId
                   AND STATUS IN ('COMPLETED','PENDING_REVIEW','EXPIRED')
                """, Map.of("assignmentId", assignmentId, "studentId", student.internalId(), "organizationId", student.organizationId()), Integer.class);
        return value == null ? 0 : value;
    }

    private void assertInProgress(AttemptRow attempt) {
        if (!"IN_PROGRESS".equals(attempt.status())) throw new BusinessException("EVALUATION_ATTEMPT_CLOSED", "Este intento ya no acepta respuestas.");
        if (attempt.expiresAt() != null && !attempt.expiresAt().isAfter(clock.instant())) {
            jdbc.update("UPDATE STUDENT_EVALUATION_ATTEMPT SET STATUS='EXPIRED', UPDATED_AT=SYSTIMESTAMP, VERSION_NO=VERSION_NO+1 WHERE EVALUATION_ATTEMPT_ID=:id",
                    Map.of("id", attempt.id()));
            throw new BusinessException("EVALUATION_TIME_EXPIRED", "El tiempo de la evaluación terminó.");
        }
    }

    private boolean windowOpen(Instant startsAt, Instant endsAt) {
        Instant now = clock.instant();
        return (startsAt == null || !startsAt.isAfter(now)) && (endsAt == null || endsAt.isAfter(now));
    }

    private static String canonical(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("PUBLIC_ID_REQUIRED", "El identificador es obligatorio.");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private static String join(Collection<String> values) { return values == null || values.isEmpty() ? null : String.join(",", values); }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : List.of(value.split(",")); }
    private String writeJson(Map<String, String> value) { try { return value == null || value.isEmpty() ? null : json.writeValueAsString(value); } catch (Exception e) { throw new BusinessException("EVALUATION_ANSWER_INVALID", "No fue posible guardar la respuesta."); } }
    private Map<String, String> readPairs(String value) { try { return value == null || value.isBlank() ? Map.of() : json.readValue(value, new TypeReference<Map<String, String>>() {}); } catch (Exception e) { return Map.of(); } }
    private List<String> acceptedAnswers(String value) { try { return value == null || value.isBlank() ? List.of() : json.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception e) { return List.of(); } }
    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value; }
    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }
    private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value == 1; }
    private static Instant timestampInstant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static OffsetDateTime timestampOffset(Timestamp value) { return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC); }

    public record FormOption(String publicId, String title, String description) {}
    private record FormRow(Long id, String publicId, String title, String description, String contentMode,
            BigDecimal passingScore, Integer maxAttempts, boolean retryUntilPassed, Integer durationMinutes,
            boolean randomizeQuestions, boolean randomizeOptions, boolean showProgress, boolean showResults,
            boolean showCorrectAnswers, boolean hideQuestionNumbers, boolean allowSaveResume,
            boolean oneActiveAttempt, String thankYouMessage, Instant startsAt, Instant endsAt) {}
    private record StudentTarget(Long studentId, String publicId, Long organizationId, String organizationPublicId,
            String organizationCode, String organizationName, String studentCode, String email, String firstName,
            String lastName, String displayName) {
        AuthenticatedStudent asAuthenticatedStudent() {
            return new AuthenticatedStudent(studentId, publicId, organizationId, organizationPublicId, organizationCode,
                    organizationName, studentCode, email, firstName, lastName, displayName, null, null, null, null, false);
        }
    }
    private record AssignmentRow(Long id, String publicId, Long formId, String status, Instant dueAt) {}
    private record AttemptRow(Long id, String publicId, Long assignmentId, String assignmentPublicId, Long formId,
            String formPublicId, String formTitle, int attemptNumber, String status, Instant startedAt, Instant expiresAt,
            Instant submittedAt, BigDecimal totalPoints, BigDecimal passingScore, Integer maxAttempts,
            Integer durationMinutes, boolean showProgress, boolean showResults, boolean showCorrectAnswers,
            boolean hideQuestionNumbers, boolean allowSaveResume, boolean randomizeOptions, String thankYouMessage,
            BigDecimal score, Boolean passed) {}
    private record AttemptQuestionRow(Long id, String publicId, Long questionId, int order, BigDecimal points,
            boolean answered, List<String> selectedIds, Map<String, String> matchingPairs, String textAnswer) {}
    private record QuestionSelection(Long questionId, BigDecimal points) {}
    private record PoolRow(String sourceType, Long categoryId, Long collectionId, int count, String difficultyCode) {}
    private record ReviewRow(String attemptQuestionPublicId, Long questionId, Boolean correct) {}
    private record ScoredAnswer(List<String> selectedIds, Map<String, String> matchingPairs, String textAnswer,
            Boolean correct, BigDecimal earnedPoints) {}
}
