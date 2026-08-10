package com.nexoskill.evaluation.development.application.service;

import static com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.*;

import com.nexoskill.evaluation.development.infrastructure.persistence.StudentStudyQuestionRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionJpaEntity;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionOptionJpaEntity;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPracticeService {
    private static final Set<Integer> ALLOWED_COUNTS = Set.of(5, 10, 20);
    private static final Set<String> MODES = Set.of("QUICK", "SIMULATOR", "REVIEW_ERRORS", "TOPIC");
    private static final String ELIGIBILITY = """
            q.STATUS = 'ACTIVE'
            AND q.TYPE_CODE IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')
            AND (
                (q.CONTENT_SCOPE = 'ORGANIZATION' AND q.OWNER_ORGANIZATION_ID = :organizationId)
                OR (
                    q.CONTENT_SCOPE = 'GLOBAL'
                    AND (
                        NVL(q.AVAILABILITY_MODE, 'GLOBAL') = 'GLOBAL'
                        OR EXISTS (
                            SELECT 1 FROM QUESTION_ORGANIZATION_AVAILABILITY availability
                             WHERE availability.QUESTION_ID = q.QUESTION_ID
                               AND availability.ORGANIZATION_ID = :organizationId
                               AND availability.STATUS = 'ACTIVE'
                        )
                    )
                    AND (
                        NOT EXISTS (
                            SELECT 1 FROM GLOBAL_CONTENT_VERSION any_version
                             WHERE any_version.CONTENT_TYPE = 'QUESTION'
                               AND any_version.CONTENT_ID = q.QUESTION_ID
                        )
                        OR EXISTS (
                            SELECT 1 FROM GLOBAL_CONTENT_VERSION published
                             WHERE published.CONTENT_TYPE = 'QUESTION'
                               AND published.CONTENT_ID = q.QUESTION_ID
                               AND published.EDITORIAL_STATUS = 'PUBLISHED'
                        )
                    )
                )
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final StudentStudyQuestionRepository questions;
    private final Clock clock;

    public StudentPracticeService(NamedParameterJdbcTemplate jdbc, StudentStudyQuestionRepository questions, Clock clock) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudyOptions options(AuthenticatedStudent student) {
        MapSqlParameterSource params = new MapSqlParameterSource("organizationId", student.organizationId());
        long available = requiredLong("SELECT COUNT(*) FROM QUESTION q WHERE " + ELIGIBILITY, params);
        long errors = reviewErrorQuestionIds(student).size();

        List<Option> technologies = jdbc.query("""
                SELECT technology.PUBLIC_ID, technology.TECHNOLOGY_NAME, COUNT(DISTINCT q.QUESTION_ID) ITEM_COUNT
                  FROM QUESTION q
                  JOIN QUESTION_TECHNOLOGY technology ON technology.TECHNOLOGY_ID = q.TECHNOLOGY_ID
                 WHERE """ + ELIGIBILITY + """
                   AND technology.STATUS = 'ACTIVE'
                 GROUP BY technology.PUBLIC_ID, technology.TECHNOLOGY_NAME
                 ORDER BY LOWER(technology.TECHNOLOGY_NAME)
                """, params, (rs, rowNum) -> new Option(rs.getString(1), rs.getString(2), rs.getLong(3)));

        List<Option> categories = jdbc.query("""
                SELECT category.PUBLIC_ID, category.CATEGORY_NAME, COUNT(DISTINCT q.QUESTION_ID) ITEM_COUNT
                  FROM QUESTION q
                  JOIN QUESTION_CATEGORY_RELATION relation ON relation.QUESTION_ID = q.QUESTION_ID
                  JOIN QUESTION_CATEGORY category ON category.CATEGORY_ID = relation.CATEGORY_ID
                 WHERE """ + ELIGIBILITY + """
                   AND category.STATUS = 'ACTIVE'
                 GROUP BY category.PUBLIC_ID, category.CATEGORY_NAME
                 ORDER BY LOWER(category.CATEGORY_NAME)
                """, params, (rs, rowNum) -> new Option(rs.getString(1), rs.getString(2), rs.getLong(3)));
        return new StudyOptions(technologies, categories, available, errors);
    }

    @Transactional
    public PracticeSession start(AuthenticatedStudent student, StartPracticeCommand command) {
        if (command == null) throw new BusinessException("PRACTICE_REQUEST_REQUIRED", "Selecciona cómo quieres practicar.");
        String mode = normalizeMode(command.mode());
        int count = command.questionCount() == null ? 5 : command.questionCount();
        if (!ALLOWED_COUNTS.contains(count)) {
            throw new BusinessException("PRACTICE_COUNT_INVALID", "Selecciona 5, 10 o 20 preguntas.");
        }
        Long technologyId = resolveTechnology(student, command.technologyPublicId());
        Long categoryId = resolveCategory(student, command.categoryPublicId());
        if ("TOPIC".equals(mode) && technologyId == null && categoryId == null) {
            throw new BusinessException("PRACTICE_TOPIC_REQUIRED", "Selecciona una tecnología o un tema para estudiar.");
        }

        List<QuestionJpaEntity> selected;
        if ("REVIEW_ERRORS".equals(mode)) {
            List<Long> errorIds = reviewErrorQuestionIds(student);
            if (errorIds.isEmpty()) {
                throw new BusinessException("PRACTICE_NO_ERRORS", "Aún no tienes preguntas pendientes de repaso.");
            }
            selected = questions.findRandomEligibleByIds(student.organizationId(), errorIds, PageRequest.of(0, count));
            if (technologyId != null || categoryId != null) {
                selected = selected.stream().filter(q -> matches(q, technologyId, categoryId)).limit(count).toList();
            }
        } else {
            selected = questions.findRandomEligible(student.organizationId(), technologyId, categoryId,
                    PageRequest.of(0, count));
        }
        if (selected.isEmpty()) {
            throw new BusinessException("PRACTICE_NO_QUESTIONS",
                    "No hay preguntas activas disponibles para los criterios seleccionados.");
        }

        String sessionPublicId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("publicId", sessionPublicId)
                .addValue("studentId", student.internalId())
                .addValue("organizationId", student.organizationId())
                .addValue("mode", mode)
                .addValue("technologyId", technologyId)
                .addValue("categoryId", categoryId)
                .addValue("requestedCount", count)
                .addValue("total", selected.size())
                .addValue("now", now);
        jdbc.update("""
                INSERT INTO STUDENT_PRACTICE_SESSION
                    (PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, MODE_CODE, TECHNOLOGY_ID, CATEGORY_ID,
                     REQUESTED_COUNT, TOTAL_QUESTIONS, ANSWERED_QUESTIONS, CORRECT_ANSWERS,
                     STATUS, STARTED_AT, UPDATED_AT, VERSION_NO)
                VALUES (:publicId, :studentId, :organizationId, :mode, :technologyId, :categoryId,
                        :requestedCount, :total, 0, 0, 'IN_PROGRESS', :now, :now, 0)
                """, params);
        Long sessionId = jdbc.queryForObject(
                "SELECT PRACTICE_SESSION_ID FROM STUDENT_PRACTICE_SESSION WHERE PUBLIC_ID = :publicId",
                Map.of("publicId", sessionPublicId), Long.class);
        int order = 1;
        for (QuestionJpaEntity question : selected) {
            jdbc.update("""
                    INSERT INTO STUDENT_PRACTICE_QUESTION
                        (PUBLIC_ID, PRACTICE_SESSION_ID, QUESTION_ID, QUESTION_ORDER)
                    VALUES (:publicId, :sessionId, :questionId, :questionOrder)
                    """, Map.of("publicId", UUID.randomUUID().toString(), "sessionId", sessionId,
                            "questionId", question.getId(), "questionOrder", order++));
        }
        return get(student, sessionPublicId);
    }

    @Transactional(readOnly = true)
    public PracticeSession get(AuthenticatedStudent student, String publicId) {
        SessionRow session = requireSession(student, publicId, false);
        List<QuestionRow> rows = jdbc.query("""
                SELECT sq.SESSION_QUESTION_ID, sq.PUBLIC_ID, sq.QUESTION_ID, sq.QUESTION_ORDER,
                       answer.SELECTED_OPTION_IDS, answer.IS_CORRECT, answer.ANSWERED_AT
                  FROM STUDENT_PRACTICE_QUESTION sq
                  LEFT JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                 WHERE sq.PRACTICE_SESSION_ID = :sessionId
                 ORDER BY sq.QUESTION_ORDER
                """, Map.of("sessionId", session.id()), (rs, rowNum) -> new QuestionRow(
                        rs.getLong("SESSION_QUESTION_ID"), rs.getString("PUBLIC_ID"), rs.getLong("QUESTION_ID"),
                        rs.getInt("QUESTION_ORDER"), rs.getString("SELECTED_OPTION_IDS"),
                        rs.getObject("IS_CORRECT") == null ? null : rs.getInt("IS_CORRECT") == 1,
                        rs.getTimestamp("ANSWERED_AT") == null ? null : rs.getTimestamp("ANSWERED_AT").toInstant()));
        Map<Long, QuestionJpaEntity> entities = questions.findAllByIdIn(rows.stream().map(QuestionRow::questionId).toList())
                .stream().collect(Collectors.toMap(QuestionJpaEntity::getId, Function.identity()));
        boolean revealAnswers = "COMPLETED".equals(session.status()) || !"SIMULATOR".equals(session.mode());
        List<PracticeQuestion> views = rows.stream()
                .map(row -> toPracticeQuestion(row, entities.get(row.questionId()), rows.size(), revealAnswers))
                .filter(java.util.Objects::nonNull)
                .toList();
        PracticeQuestion current = views.stream().filter(view -> !view.answered()).findFirst().orElse(null);
        return new PracticeSession(session.publicId(), session.mode(), session.status(), session.technology(),
                session.category(), session.requestedCount(), session.totalQuestions(), session.answeredQuestions(),
                session.correctAnswers(), session.startedAt(), session.completedAt(), !"SIMULATOR".equals(session.mode()),
                current, views);
    }

    @Transactional
    public PracticeFeedback answer(AuthenticatedStudent student, String sessionPublicId,
            String sessionQuestionPublicId, PracticeAnswerCommand command) {
        SessionRow session = requireSession(student, sessionPublicId, true);
        if (!"IN_PROGRESS".equals(session.status())) {
            throw new BusinessException("PRACTICE_ALREADY_COMPLETED", "Esta práctica ya finalizó.");
        }
        QuestionRow row = jdbc.query("""
                SELECT sq.SESSION_QUESTION_ID, sq.PUBLIC_ID, sq.QUESTION_ID, sq.QUESTION_ORDER,
                       answer.SELECTED_OPTION_IDS, answer.IS_CORRECT, answer.ANSWERED_AT
                  FROM STUDENT_PRACTICE_QUESTION sq
                  LEFT JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                 WHERE sq.PRACTICE_SESSION_ID = :sessionId AND sq.PUBLIC_ID = :publicId
                """, Map.of("sessionId", session.id(), "publicId", canonical(sessionQuestionPublicId)),
                rs -> rs.next() ? new QuestionRow(rs.getLong("SESSION_QUESTION_ID"), rs.getString("PUBLIC_ID"),
                        rs.getLong("QUESTION_ID"), rs.getInt("QUESTION_ORDER"), rs.getString("SELECTED_OPTION_IDS"),
                        rs.getObject("IS_CORRECT") == null ? null : rs.getInt("IS_CORRECT") == 1,
                        rs.getTimestamp("ANSWERED_AT") == null ? null : rs.getTimestamp("ANSWERED_AT").toInstant()) : null);
        if (row == null) throw new BusinessException("PRACTICE_QUESTION_NOT_FOUND", "La pregunta no pertenece a esta práctica.");
        if (row.answeredAt() != null) throw new BusinessException("PRACTICE_QUESTION_ALREADY_ANSWERED", "Esta pregunta ya fue respondida.");
        QuestionJpaEntity question = questions.findById(row.questionId())
                .orElseThrow(() -> new BusinessException("PRACTICE_QUESTION_NOT_FOUND", "La pregunta ya no está disponible."));
        AnswerEvaluation evaluation = evaluateChoice(question, command == null ? List.of() : command.selectedOptionPublicIds());
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO STUDENT_PRACTICE_ANSWER
                    (PUBLIC_ID, SESSION_QUESTION_ID, SELECTED_OPTION_IDS, IS_CORRECT, ANSWERED_AT)
                VALUES (:publicId, :sessionQuestionId, :selected, :correct, :now)
                """, new MapSqlParameterSource()
                        .addValue("publicId", UUID.randomUUID().toString())
                        .addValue("sessionQuestionId", row.id())
                        .addValue("selected", encodeIds(evaluation.selected()))
                        .addValue("correct", evaluation.correct() ? 1 : 0)
                        .addValue("now", now));

        Counts counts = counts(session.id());
        boolean completed = counts.answered() >= session.totalQuestions();
        jdbc.update("""
                UPDATE STUDENT_PRACTICE_SESSION
                   SET ANSWERED_QUESTIONS = :answered,
                       CORRECT_ANSWERS = :correct,
                       STATUS = :status,
                       COMPLETED_AT = :completedAt,
                       UPDATED_AT = :now,
                       VERSION_NO = VERSION_NO + 1
                 WHERE PRACTICE_SESSION_ID = :sessionId
                """, new MapSqlParameterSource()
                        .addValue("answered", counts.answered())
                        .addValue("correct", counts.correct())
                        .addValue("status", completed ? "COMPLETED" : "IN_PROGRESS")
                        .addValue("completedAt", completed ? now : null)
                        .addValue("now", now)
                        .addValue("sessionId", session.id()));

        PracticeQuestion next = completed ? null : get(student, sessionPublicId).currentQuestion();
        boolean reveal = !"SIMULATOR".equals(session.mode()) || completed;
        return new PracticeFeedback(reveal ? evaluation.correct() : null, reveal ? evaluation.correctIds() : List.of(),
                reveal ? question.getExplanation() : null, counts.answered(), counts.correct(), completed, next);
    }

    @Transactional(readOnly = true)
    public PracticeResult result(AuthenticatedStudent student, String sessionPublicId) {
        SessionRow session = requireSession(student, sessionPublicId, false);
        if (!"COMPLETED".equals(session.status())) {
            throw new BusinessException("PRACTICE_NOT_COMPLETED", "Finaliza la práctica para consultar el resultado.");
        }
        BigDecimal percentage = percentage(session.correctAnswers(), session.totalQuestions());
        List<PreparationPoint> byTopic = jdbc.query("""
                SELECT category.PUBLIC_ID CATEGORY_KEY, category.CATEGORY_NAME LABEL,
                       COUNT(*) ANSWERED, SUM(answer.IS_CORRECT) CORRECT
                  FROM STUDENT_PRACTICE_QUESTION sq
                  JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                  JOIN QUESTION_CATEGORY_RELATION relation ON relation.QUESTION_ID = sq.QUESTION_ID
                  JOIN QUESTION_CATEGORY category ON category.CATEGORY_ID = relation.CATEGORY_ID
                 WHERE sq.PRACTICE_SESSION_ID = :sessionId
                 GROUP BY category.PUBLIC_ID, category.CATEGORY_NAME
                 ORDER BY COUNT(*) DESC, LOWER(category.CATEGORY_NAME)
                """, Map.of("sessionId", session.id()), (rs, rowNum) -> new PreparationPoint(
                        rs.getString("CATEGORY_KEY"), rs.getString("LABEL"), rs.getInt("ANSWERED"),
                        rs.getInt("CORRECT"), percentage(rs.getInt("CORRECT"), rs.getInt("ANSWERED"))));
        List<String> incorrect = jdbc.query("""
                SELECT q.PUBLIC_ID
                  FROM STUDENT_PRACTICE_QUESTION sq
                  JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                  JOIN QUESTION q ON q.QUESTION_ID = sq.QUESTION_ID
                 WHERE sq.PRACTICE_SESSION_ID = :sessionId AND answer.IS_CORRECT = 0
                 ORDER BY sq.QUESTION_ORDER
                """, Map.of("sessionId", session.id()), (rs, rowNum) -> rs.getString(1));
        return new PracticeResult(session.publicId(), session.mode(), session.totalQuestions(),
                session.answeredQuestions(), session.correctAnswers(), percentage, byTopic, incorrect,
                session.completedAt());
    }

    @Transactional
    public void abandon(AuthenticatedStudent student, String sessionPublicId) {
        SessionRow session = requireSession(student, sessionPublicId, true);
        if (!"IN_PROGRESS".equals(session.status())) return;
        jdbc.update("""
                UPDATE STUDENT_PRACTICE_SESSION
                   SET STATUS = 'ABANDONED', UPDATED_AT = :now, VERSION_NO = VERSION_NO + 1
                 WHERE PRACTICE_SESSION_ID = :id
                """, Map.of("now", clock.instant(), "id", session.id()));
    }

    @Transactional(readOnly = true)
    public long availableQuestionCount(AuthenticatedStudent student) {
        return requiredLong("SELECT COUNT(*) FROM QUESTION q WHERE " + ELIGIBILITY,
                new MapSqlParameterSource("organizationId", student.organizationId()));
    }

    @Transactional(readOnly = true)
    public List<PreparationPoint> preparation(AuthenticatedStudent student, int minimumAnswers) {
        return jdbc.query("""
                SELECT category.PUBLIC_ID CATEGORY_KEY, category.CATEGORY_NAME LABEL,
                       COUNT(*) ANSWERED, SUM(answer.IS_CORRECT) CORRECT
                  FROM STUDENT_PRACTICE_SESSION session_value
                  JOIN STUDENT_PRACTICE_QUESTION sq ON sq.PRACTICE_SESSION_ID = session_value.PRACTICE_SESSION_ID
                  JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                  JOIN QUESTION_CATEGORY_RELATION relation ON relation.QUESTION_ID = sq.QUESTION_ID
                  JOIN QUESTION_CATEGORY category ON category.CATEGORY_ID = relation.CATEGORY_ID
                 WHERE session_value.STUDENT_ID = :studentId
                   AND session_value.ORGANIZATION_ID = :organizationId
                 GROUP BY category.PUBLIC_ID, category.CATEGORY_NAME
                HAVING COUNT(*) >= :minimumAnswers
                 ORDER BY (SUM(answer.IS_CORRECT) / COUNT(*)) DESC, COUNT(*) DESC
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId(),
                        "minimumAnswers", minimumAnswers), (rs, rowNum) -> new PreparationPoint(
                        rs.getString("CATEGORY_KEY"), rs.getString("LABEL"), rs.getInt("ANSWERED"),
                        rs.getInt("CORRECT"), percentage(rs.getInt("CORRECT"), rs.getInt("ANSWERED"))));
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> trend(AuthenticatedStudent student) {
        return jdbc.query("""
                SELECT TO_CHAR(session_value.COMPLETED_AT, 'YYYY-MM') PERIOD_KEY,
                       COUNT(answer.PRACTICE_ANSWER_ID) ANSWERED,
                       NVL(SUM(answer.IS_CORRECT), 0) CORRECT
                  FROM STUDENT_PRACTICE_SESSION session_value
                  JOIN STUDENT_PRACTICE_QUESTION sq ON sq.PRACTICE_SESSION_ID = session_value.PRACTICE_SESSION_ID
                  JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                 WHERE session_value.STUDENT_ID = :studentId
                   AND session_value.ORGANIZATION_ID = :organizationId
                   AND session_value.STATUS = 'COMPLETED'
                   AND session_value.COMPLETED_AT >= ADD_MONTHS(SYSTIMESTAMP, -5)
                 GROUP BY TO_CHAR(session_value.COMPLETED_AT, 'YYYY-MM')
                 ORDER BY PERIOD_KEY
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new TrendPoint(rs.getString("PERIOD_KEY"), rs.getInt("ANSWERED"),
                        rs.getInt("CORRECT"), percentage(rs.getInt("CORRECT"), rs.getInt("ANSWERED"))));
    }

    @Transactional(readOnly = true)
    public ContinueItem activePractice(AuthenticatedStudent student) {
        List<ContinueItem> rows = jdbc.query("""
                SELECT * FROM (
                    SELECT session_value.PUBLIC_ID, session_value.MODE_CODE, session_value.ANSWERED_QUESTIONS,
                           session_value.TOTAL_QUESTIONS, session_value.STARTED_AT
                      FROM STUDENT_PRACTICE_SESSION session_value
                     WHERE session_value.STUDENT_ID = :studentId
                       AND session_value.ORGANIZATION_ID = :organizationId
                       AND session_value.STATUS = 'IN_PROGRESS'
                     ORDER BY session_value.UPDATED_AT DESC
                ) WHERE ROWNUM = 1
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new ContinueItem("PRACTICE", practiceModeLabel(rs.getString("MODE_CODE")),
                        "Práctica · " + rs.getInt("ANSWERED_QUESTIONS") + " de " + rs.getInt("TOTAL_QUESTIONS"),
                        rs.getInt("ANSWERED_QUESTIONS"), rs.getInt("TOTAL_QUESTIONS"),
                        percentage(rs.getInt("ANSWERED_QUESTIONS"), rs.getInt("TOTAL_QUESTIONS")),
                        "/student/study/practice/" + rs.getString("PUBLIC_ID")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> recentActivity(AuthenticatedStudent student, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return jdbc.query("""
                SELECT OCCURRED_AT, EVENT_TYPE, TITLE, DESCRIPTION
                  FROM (
                        SELECT activity_value.*,
                               ROW_NUMBER() OVER (ORDER BY activity_value.OCCURRED_AT DESC) ACTIVITY_RN
                          FROM (
                                SELECT session_value.COMPLETED_AT OCCURRED_AT,
                                       'PRACTICE_COMPLETED' EVENT_TYPE,
                                       CASE session_value.MODE_CODE
                                         WHEN 'SIMULATOR' THEN 'Completaste un simulador'
                                         WHEN 'REVIEW_ERRORS' THEN 'Completaste un repaso de errores'
                                         WHEN 'TOPIC' THEN 'Completaste una práctica por tema'
                                         ELSE 'Completaste una práctica rápida'
                                       END TITLE,
                                       session_value.CORRECT_ANSWERS || ' de ' || session_value.TOTAL_QUESTIONS || ' respuestas correctas' DESCRIPTION
                                  FROM STUDENT_PRACTICE_SESSION session_value
                                 WHERE session_value.STUDENT_ID = :studentId
                                   AND session_value.ORGANIZATION_ID = :organizationId
                                   AND session_value.STATUS = 'COMPLETED'
                                UNION ALL
                                SELECT session_value.STARTED_AT,
                                       'PRACTICE_STARTED',
                                       'Iniciaste una actividad de estudio',
                                       CASE session_value.MODE_CODE
                                         WHEN 'SIMULATOR' THEN 'Simulador'
                                         WHEN 'REVIEW_ERRORS' THEN 'Repasar errores'
                                         WHEN 'TOPIC' THEN 'Estudiar por tema'
                                         ELSE 'Práctica rápida'
                                       END
                                  FROM STUDENT_PRACTICE_SESSION session_value
                                 WHERE session_value.STUDENT_ID = :studentId
                                   AND session_value.ORGANIZATION_ID = :organizationId
                               ) activity_value
                         WHERE activity_value.OCCURRED_AT IS NOT NULL
                       ) ranked_activity
                 WHERE ACTIVITY_RN <= :limit
                 ORDER BY OCCURRED_AT DESC
                """, new MapSqlParameterSource()
                        .addValue("studentId", student.internalId())
                        .addValue("organizationId", student.organizationId())
                        .addValue("limit", safeLimit),
                (rs, rowNum) -> new ActivityItem(rs.getString("EVENT_TYPE"),
                        rs.getTimestamp("OCCURRED_AT").toInstant(), rs.getString("TITLE"), rs.getString("DESCRIPTION")));
    }

    private List<Long> reviewErrorQuestionIds(AuthenticatedStudent student) {
        return jdbc.query("""
                SELECT QUESTION_ID
                  FROM (
                        SELECT ranked.QUESTION_ID,
                               MAX(CASE WHEN ranked.IS_CORRECT = 0 THEN 1 ELSE 0 END) HAD_WRONG,
                               SUM(CASE WHEN ranked.RN <= 2 AND ranked.IS_CORRECT = 1 THEN 1 ELSE 0 END) RECENT_CORRECT,
                               COUNT(CASE WHEN ranked.RN <= 2 THEN 1 END) RECENT_COUNT
                          FROM (
                                SELECT sq.QUESTION_ID, answer.IS_CORRECT,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY sq.QUESTION_ID
                                           ORDER BY answer.ANSWERED_AT DESC, answer.PRACTICE_ANSWER_ID DESC
                                       ) RN
                                  FROM STUDENT_PRACTICE_SESSION session_value
                                  JOIN STUDENT_PRACTICE_QUESTION sq ON sq.PRACTICE_SESSION_ID = session_value.PRACTICE_SESSION_ID
                                  JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                                 WHERE session_value.STUDENT_ID = :studentId
                                   AND session_value.ORGANIZATION_ID = :organizationId
                               ) ranked
                         GROUP BY ranked.QUESTION_ID
                       ) review_value
                 WHERE HAD_WRONG = 1 AND (RECENT_COUNT < 2 OR RECENT_CORRECT < 2)
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> rs.getLong("QUESTION_ID"));
    }

    private SessionRow requireSession(AuthenticatedStudent student, String publicId, boolean forUpdate) {
        String sql = """
                SELECT session_value.PRACTICE_SESSION_ID, session_value.PUBLIC_ID, session_value.MODE_CODE,
                       session_value.REQUESTED_COUNT, session_value.TOTAL_QUESTIONS,
                       session_value.ANSWERED_QUESTIONS, session_value.CORRECT_ANSWERS,
                       session_value.STATUS, session_value.STARTED_AT, session_value.COMPLETED_AT,
                       technology.TECHNOLOGY_NAME, category.CATEGORY_NAME
                  FROM STUDENT_PRACTICE_SESSION session_value
                  LEFT JOIN QUESTION_TECHNOLOGY technology ON technology.TECHNOLOGY_ID = session_value.TECHNOLOGY_ID
                  LEFT JOIN QUESTION_CATEGORY category ON category.CATEGORY_ID = session_value.CATEGORY_ID
                 WHERE session_value.PUBLIC_ID = :publicId
                   AND session_value.STUDENT_ID = :studentId
                   AND session_value.ORGANIZATION_ID = :organizationId
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<SessionRow> rows = jdbc.query(sql,
                Map.of("publicId", canonical(publicId), "studentId", student.internalId(),
                        "organizationId", student.organizationId()),
                (rs, rowNum) -> new SessionRow(rs.getLong("PRACTICE_SESSION_ID"), rs.getString("PUBLIC_ID"),
                        rs.getString("MODE_CODE"), rs.getInt("REQUESTED_COUNT"), rs.getInt("TOTAL_QUESTIONS"),
                        rs.getInt("ANSWERED_QUESTIONS"), rs.getInt("CORRECT_ANSWERS"), rs.getString("STATUS"),
                        rs.getTimestamp("STARTED_AT").toInstant(),
                        rs.getTimestamp("COMPLETED_AT") == null ? null : rs.getTimestamp("COMPLETED_AT").toInstant(),
                        rs.getString("TECHNOLOGY_NAME"), rs.getString("CATEGORY_NAME")));
        if (rows.isEmpty()) {
            throw new BusinessException("PRACTICE_NOT_FOUND", "La práctica no existe o no pertenece a tu cuenta.");
        }
        return rows.getFirst();
    }

    private PracticeQuestion toPracticeQuestion(QuestionRow row, QuestionJpaEntity question, int total,
            boolean revealAnswers) {
        if (question == null) return null;
        List<QuestionOption> options = question.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOptionJpaEntity::getOptionOrder))
                .map(option -> new QuestionOption(option.getPublicId(), option.getText()))
                .toList();
        boolean answered = row.answeredAt() != null;
        List<String> selected = decodeIds(row.selectedIds());
        List<String> correctIds = answered && revealAnswers ? correctIds(question) : List.of();
        String explanation = answered && revealAnswers ? question.getExplanation() : null;
        return new PracticeQuestion(row.publicId(), question.getPublicId(), row.order(), total,
                question.getType().getCode(), question.getStatement(), question.getCodeLanguage(),
                question.getCodeContent(), question.getTechnology() == null ? null : question.getTechnology().getName(),
                question.getCategories().stream().map(category -> category.getName()).sorted().toList(), options,
                answered, row.correct(), selected, correctIds, explanation);
    }

    private AnswerEvaluation evaluateChoice(QuestionJpaEntity question, Collection<String> selectedValues) {
        String type = question.getType().getCode();
        if (!Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(type)) {
            throw new BusinessException("PRACTICE_QUESTION_TYPE_UNSUPPORTED",
                    "Esta pregunta no es compatible con el modo práctica.");
        }
        Set<String> available = question.getOptions().stream().map(QuestionOptionJpaEntity::getPublicId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> selected = selectedValues == null ? new LinkedHashSet<>() : selectedValues.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty()) throw new BusinessException("PRACTICE_ANSWER_REQUIRED", "Selecciona una respuesta.");
        if (!available.containsAll(selected)) {
            throw new BusinessException("PRACTICE_ANSWER_INVALID", "La respuesta contiene una opción que no pertenece a la pregunta.");
        }
        if (("SINGLE_CHOICE".equals(type) || "TRUE_FALSE".equals(type)) && selected.size() != 1) {
            throw new BusinessException("PRACTICE_SINGLE_ANSWER_REQUIRED", "Selecciona una sola respuesta.");
        }
        List<String> correctIds = correctIds(question);
        boolean correct = new HashSet<>(correctIds).equals(selected);
        return new AnswerEvaluation(List.copyOf(selected), correctIds, correct);
    }

    private List<String> correctIds(QuestionJpaEntity question) {
        return question.getOptions().stream().filter(QuestionOptionJpaEntity::isCorrect)
                .map(QuestionOptionJpaEntity::getPublicId).sorted().toList();
    }

    private Counts counts(Long sessionId) {
        return jdbc.query("""
                SELECT COUNT(answer.PRACTICE_ANSWER_ID) ANSWERED,
                       NVL(SUM(answer.IS_CORRECT), 0) CORRECT
                  FROM STUDENT_PRACTICE_QUESTION sq
                  LEFT JOIN STUDENT_PRACTICE_ANSWER answer ON answer.SESSION_QUESTION_ID = sq.SESSION_QUESTION_ID
                 WHERE sq.PRACTICE_SESSION_ID = :sessionId
                """, Map.of("sessionId", sessionId), rs -> {
            rs.next();
            return new Counts(rs.getInt("ANSWERED"), rs.getInt("CORRECT"));
        });
    }

    private Long resolveTechnology(AuthenticatedStudent student, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        List<Long> values = jdbc.query("""
                SELECT TECHNOLOGY_ID FROM QUESTION_TECHNOLOGY
                 WHERE PUBLIC_ID = :publicId AND STATUS = 'ACTIVE'
                   AND (CONTENT_SCOPE = 'GLOBAL' OR OWNER_ORGANIZATION_ID = :organizationId)
                """, Map.of("publicId", publicId.trim(), "organizationId", student.organizationId()),
                (rs, rowNum) -> rs.getLong(1));
        if (values.isEmpty()) throw new BusinessException("PRACTICE_TECHNOLOGY_INVALID", "La tecnología seleccionada no está disponible.");
        return values.getFirst();
    }

    private Long resolveCategory(AuthenticatedStudent student, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        List<Long> values = jdbc.query("""
                SELECT CATEGORY_ID FROM QUESTION_CATEGORY
                 WHERE PUBLIC_ID = :publicId AND STATUS = 'ACTIVE'
                   AND (CONTENT_SCOPE = 'GLOBAL' OR OWNER_ORGANIZATION_ID = :organizationId)
                """, Map.of("publicId", publicId.trim(), "organizationId", student.organizationId()),
                (rs, rowNum) -> rs.getLong(1));
        if (values.isEmpty()) throw new BusinessException("PRACTICE_CATEGORY_INVALID", "El tema seleccionado no está disponible.");
        return values.getFirst();
    }

    private boolean matches(QuestionJpaEntity q, Long technologyId, Long categoryId) {
        if (technologyId != null && (q.getTechnology() == null || !technologyId.equals(q.getTechnology().getId()))) return false;
        return categoryId == null || q.getCategories().stream().anyMatch(category -> categoryId.equals(category.getId()));
    }

    private long requiredLong(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static String normalizeMode(String value) {
        String mode = value == null ? "QUICK" : value.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw new BusinessException("PRACTICE_MODE_INVALID", "El modo de estudio seleccionado no es válido.");
        return mode;
    }

    private static String canonical(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("IDENTIFIER_REQUIRED", "El identificador es obligatorio.");
        return value.trim();
    }

    private static String encodeIds(Collection<String> values) {
        return values.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().sorted()
                .collect(Collectors.joining(","));
    }

    private static List<String> decodeIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static BigDecimal percentage(int correct, int total) {
        if (total <= 0) return null;
        return BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static String practiceModeLabel(String mode) {
        return switch (mode) {
            case "SIMULATOR" -> "Simulador";
            case "REVIEW_ERRORS" -> "Repasar errores";
            case "TOPIC" -> "Estudiar por tema";
            default -> "Práctica rápida";
        };
    }

    private record SessionRow(Long id, String publicId, String mode, int requestedCount, int totalQuestions,
            int answeredQuestions, int correctAnswers, String status, Instant startedAt, Instant completedAt,
            String technology, String category) {}
    private record QuestionRow(Long id, String publicId, Long questionId, int order, String selectedIds,
            Boolean correct, Instant answeredAt) {}
    private record Counts(int answered, int correct) {}
    private record AnswerEvaluation(List<String> selected, List<String> correctIds, boolean correct) {}
}
