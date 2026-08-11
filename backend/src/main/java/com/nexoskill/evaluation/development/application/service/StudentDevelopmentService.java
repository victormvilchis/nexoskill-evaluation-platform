package com.nexoskill.evaluation.development.application.service;

import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.ActionLink;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.ActivityItem;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.CertificationCard;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.ContinueItem;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.EvaluationCard;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.Focus;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.HealthCheck;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.HomeView;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.PreparationPoint;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.PathCard;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.Profile;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.Recommendation;
import com.nexoskill.evaluation.development.application.model.StudentDevelopmentModels.TrendPoint;
import com.nexoskill.evaluation.students.infrastructure.security.AuthenticatedStudent;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDevelopmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final StudentPracticeService practices;
    private final StudentEvaluationService evaluations;
    private final StudentPathService paths;

    public StudentDevelopmentService(NamedParameterJdbcTemplate jdbc, StudentPracticeService practices,
            StudentEvaluationService evaluations, StudentPathService paths) {
        this.jdbc = jdbc;
        this.practices = practices;
        this.evaluations = evaluations;
        this.paths = paths;
    }

    @Transactional
    public HomeView home(AuthenticatedStudent student) {
        Profile profile = profile(student);
        boolean certificationsEnabled = certificationsEnabled(student);
        List<CertificationCard> certifications = certificationsEnabled ? certifications(student) : List.of();
        String certificationFocus = certificationsEnabled ? certificationFocus(student) : null;
        List<EvaluationCard> evaluationCards = evaluations.list(student);
        List<PathCard> pathCards = paths.mine(student);
        List<PreparationPoint> preparation = practices.preparation(student, 3);
        List<PreparationPoint> strengths = preparation.stream().filter(item -> item.percentage() != null
                && item.percentage().compareTo(BigDecimal.valueOf(80)) >= 0).limit(3).toList();
        List<PreparationPoint> improvement = preparation.stream().filter(item -> item.percentage() != null
                && item.percentage().compareTo(BigDecimal.valueOf(75)) < 0)
                .sorted(Comparator.comparing(PreparationPoint::percentage)).limit(3).toList();
        HealthCheck health = health(certificationFocus, evaluationCards, pathCards, improvement);
        Focus focus = focus(certificationFocus, certifications, evaluationCards, pathCards, improvement);
        ContinueItem continueItem = practices.activePractice(student);
        if (continueItem == null) continueItem = evaluationContinue(evaluationCards);
        if (continueItem == null) continueItem = pathContinue(pathCards);
        List<Recommendation> recommendations = recommendations(certificationFocus, certifications, evaluationCards, pathCards, improvement);
        List<TrendPoint> trend = practices.trend(student);
        List<ActivityItem> activity = new ArrayList<>();
        activity.addAll(practices.recentActivity(student, 8));
        activity.addAll(evaluations.recentActivity(student, 8));
        activity.addAll(paths.recentActivity(student, 8));
        if (certificationsEnabled) activity.addAll(certificationActivity(student));
        activity = activity.stream().sorted(Comparator.comparing(ActivityItem::occurredAt).reversed()).limit(10).toList();
        return new HomeView(profile, health, focus, continueItem, certificationsEnabled,
                practices.availableQuestionCount(student), certifications, evaluationCards, pathCards,
                preparation, strengths, improvement, recommendations, trend, activity);
    }

    @Transactional(readOnly = true)
    public List<CertificationCard> certifications(AuthenticatedStudent student) {
        if (!certificationsEnabled(student)) return List.of();
        return jdbc.query("""
                SELECT cycle.CERTIFICATION_TYPE, cycle.VALIDITY_STATUS, cycle.DEADLINE_DATE,
                       NVL(cycle.LAST_APPROVED_APPLICATION_DATE, cycle.APPLICATION_DATE) APPLICATION_DATE,
                       cycle.EXPIRATION_DATE, cycle.LATEST_SCORE,
                       NVL((SELECT MAX(attempt.ATTEMPT_NUMBER) FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT attempt
                            WHERE attempt.STUDENT_CERTIFICATION_CYCLE_ID=cycle.STUDENT_CERTIFICATION_CYCLE_ID), 0) ATTEMPT_NUMBER,
                       CASE WHEN EXISTS (
                            SELECT 1 FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT attempt
                             WHERE attempt.STUDENT_CERTIFICATION_CYCLE_ID=cycle.STUDENT_CERTIFICATION_CYCLE_ID
                               AND attempt.ATTEMPT_NUMBER=2 AND (attempt.APPROVED=0 OR attempt.EXAM_STATUS IN ('FAILED','ABSENT'))
                       ) OR (NVL(cycle.IMPORTED_FAILURE_COUNT,0)>=2
                             AND (cycle.TRACKING_STATUS='NOT_APPROVED' OR cycle.LATEST_EXAM_STATUS='FAILED' OR cycle.APPROVED=0))
                       THEN 1 ELSE 0 END SECOND_ATTEMPT_FAILED,
                       technology.TECHNOLOGY_NAME, cycle.CERTIFICATION_LEVEL
                  FROM STUDENT_CERTIFICATION_CYCLE cycle
                  LEFT JOIN QUESTION_TECHNOLOGY technology ON technology.TECHNOLOGY_ID=cycle.TECHNOLOGY_ID
                 WHERE cycle.STUDENT_ID=:studentId AND cycle.ORGANIZATION_ID=:organizationId AND cycle.ACTIVE=1
                   AND (
                        (cycle.CERTIFICATION_TYPE='TECHNOLOGICAL' AND :techApplies=1)
                     OR (cycle.CERTIFICATION_TYPE='DEVELOPMENT_SECURITY' AND :dsApplies=1)
                     OR (cycle.CERTIFICATION_TYPE='NORMATIVE_TESTING' AND :normApplies=1)
                     OR (cycle.CERTIFICATION_TYPE='ONE' AND :oneApplies=1)
                     OR (cycle.CERTIFICATION_TYPE='AGILE' AND :agileApplies=1)
                     OR (cycle.CERTIFICATION_TYPE='JIRA' AND :jiraApplies=1)
                   )
                 ORDER BY CASE cycle.CERTIFICATION_TYPE
                    WHEN 'TECHNOLOGICAL' THEN 1 WHEN 'DEVELOPMENT_SECURITY' THEN 2 WHEN 'NORMATIVE_TESTING' THEN 3
                    WHEN 'ONE' THEN 4 WHEN 'AGILE' THEN 5 WHEN 'JIRA' THEN 6 ELSE 7 END,
                    cycle.IS_PRIMARY DESC, cycle.UPDATED_AT DESC
                """, applicabilityParams(student), (rs, rowNum) -> {
                    boolean secondFailed = rs.getInt("SECOND_ATTEMPT_FAILED") == 1;
                    String validity = rs.getString("VALIDITY_STATUS");
                    String status = secondFailed ? "PENDING_DEACTIVATION"
                            : "EXPIRED".equals(validity) ? "EXPIRED"
                            : "EXPIRING_SOON".equals(validity) ? "EXPIRING_SOON"
                            : "IN_RULE";
                    return new CertificationCard(rs.getString("CERTIFICATION_TYPE"),
                            certificationLabel(rs.getString("CERTIFICATION_TYPE")), status, statusLabel(status),
                            localDate(rs.getDate("DEADLINE_DATE")), localDate(rs.getDate("APPLICATION_DATE")),
                            localDate(rs.getDate("EXPIRATION_DATE")), rs.getBigDecimal("LATEST_SCORE"),
                            rs.getInt("ATTEMPT_NUMBER") == 0 ? null : rs.getInt("ATTEMPT_NUMBER"), secondFailed,
                            rs.getString("TECHNOLOGY_NAME"), rs.getString("CERTIFICATION_LEVEL"));
                });
    }

    @Transactional(readOnly = true)
    public Profile profile(AuthenticatedStudent student) {
        List<Profile> rows = jdbc.query("""
                SELECT student.DISPLAY_NAME, student.FIRST_NAME, professional.PROFILE_NAME PROFESSIONAL_PROFILE,
                       technological.PROFILE_NAME TECHNOLOGICAL_PROFILE, technology.TECHNOLOGY_NAME,
                       (SELECT certification.CERTIFICATION_LEVEL
                          FROM STUDENT_CERTIFICATION_CYCLE certification
                         WHERE certification.STUDENT_ID=student.STUDENT_ID
                           AND certification.ORGANIZATION_ID=student.ORGANIZATION_ID
                           AND certification.CERTIFICATION_TYPE='TECHNOLOGICAL' AND certification.ACTIVE=1
                         ORDER BY certification.IS_PRIMARY DESC, certification.UPDATED_AT DESC
                         FETCH FIRST 1 ROW ONLY) EXPERTISE_LEVEL
                  FROM STUDENT student
                  LEFT JOIN CERTIFICATION_PROFILE_CATALOG professional ON professional.CERTIFICATION_PROFILE_ID=student.PROFESSIONAL_PROFILE_ID
                  LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG technological ON technological.TECHNOLOGICAL_PROFILE_ID=student.TECHNOLOGICAL_PROFILE_ID
                  LEFT JOIN QUESTION_TECHNOLOGY technology ON technology.TECHNOLOGY_ID=student.TECHNOLOGY_ID
                 WHERE student.STUDENT_ID=:studentId AND student.ORGANIZATION_ID=:organizationId
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new Profile(rs.getString("DISPLAY_NAME"), rs.getString("FIRST_NAME"),
                        rs.getString("PROFESSIONAL_PROFILE"), rs.getString("TECHNOLOGICAL_PROFILE"),
                        rs.getString("TECHNOLOGY_NAME"), rs.getString("EXPERTISE_LEVEL")));
        return rows.isEmpty() ? new Profile(student.displayName(), student.firstName(), null, null, null, null) : rows.getFirst();
    }

    @Transactional(readOnly = true)
    public boolean certificationModulesEnabled(AuthenticatedStudent student) {
        return certificationsEnabled(student);
    }

    private boolean certificationsEnabled(AuthenticatedStudent student) {
        Integer value = jdbc.queryForObject("SELECT APPLIES_CERTIFICATIONS FROM ORGANIZATION WHERE ORGANIZATION_ID=:id",
                Map.of("id", student.organizationId()), Integer.class);
        return value != null && value == 1;
    }

    private String certificationFocus(AuthenticatedStudent student) {
        List<String> rows = jdbc.query("""
                SELECT FOCUS_STATUS FROM VW_STUDENT_CERTIFICATION_FOCUS
                 WHERE STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> rs.getString("FOCUS_STATUS"));
        return rows.isEmpty() ? "IN_RULE" : rows.getFirst();
    }

    private Map<String, Object> applicabilityParams(AuthenticatedStudent student) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT NVL(APPLIES_TECH_CERT,0) TECH_APPLIES, NVL(APPLIES_DEV_SECURITY,0) DS_APPLIES,
                       NVL(APPLIES_NORMATIVE_TESTING,0) NORM_APPLIES, NVL(APPLIES_ONE,0) ONE_APPLIES,
                       NVL(APPLIES_AGILE,0) AGILE_APPLIES, NVL(APPLIES_JIRA,0) JIRA_APPLIES
                  FROM STUDENT WHERE STUDENT_ID=:studentId AND ORGANIZATION_ID=:organizationId
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> Map.of("studentId", (Object) student.internalId(),
                        "organizationId", student.organizationId(),
                        "techApplies", rs.getInt("TECH_APPLIES"), "dsApplies", rs.getInt("DS_APPLIES"),
                        "normApplies", rs.getInt("NORM_APPLIES"), "oneApplies", rs.getInt("ONE_APPLIES"),
                        "agileApplies", rs.getInt("AGILE_APPLIES"), "jiraApplies", rs.getInt("JIRA_APPLIES")));
        return rows.isEmpty() ? Map.of("studentId", student.internalId(), "organizationId", student.organizationId(),
                "techApplies",0,"dsApplies",0,"normApplies",0,"oneApplies",0,"agileApplies",0,"jiraApplies",0) : rows.getFirst();
    }

    private HealthCheck health(String certificationFocus, List<EvaluationCard> evaluations,
            List<PathCard> paths, List<PreparationPoint> improvement) {
        if ("PENDING_DEACTIVATION".equals(certificationFocus))
            return new HealthCheck("ATTENTION_PRIORITY", "Atención prioritaria", "Hay una certificación con segundo intento no aprobado.");
        if ("EXPIRED".equals(certificationFocus))
            return new HealthCheck("ATTENTION_PRIORITY", "Atención prioritaria", "Tienes una certificación vencida que requiere atención.");
        if ("EXPIRING_SOON".equals(certificationFocus))
            return new HealthCheck("REQUIRES_ATTENTION", "Requiere atención", "Tienes una certificación próxima a vencer.");
        long pending = evaluations.stream().filter(item -> !"COMPLETED".equals(item.status())).count();
        if (pending > 0) return new HealthCheck("CONTINUE", "Continúa avanzando", pending == 1
                ? "Tienes una evaluación pendiente." : "Tienes " + pending + " evaluaciones pendientes.");
        long activePaths = paths.stream().filter(item -> !"COMPLETED".equals(item.status()) && !"EMPTY".equals(item.status())).count();
        if (activePaths > 0) return new HealthCheck("CONTINUE", "Continúa avanzando", activePaths == 1
                ? "Tienes un Path de desarrollo activo." : "Tienes " + activePaths + " Paths de desarrollo activos.");
        if (!improvement.isEmpty()) return new HealthCheck("CONTINUE", "Continúa avanzando", "Tu actividad reciente muestra temas que conviene reforzar.");
        return new HealthCheck("ALL_GOOD", "Todo en orden", "No hay situaciones prioritarias en tu información actual.");
    }

    private Focus focus(String certificationFocus, List<CertificationCard> certifications,
            List<EvaluationCard> evaluations, List<PathCard> paths, List<PreparationPoint> improvement) {
        CertificationCard trigger = certifications.stream().filter(item -> item.status().equals(certificationFocus)).findFirst()
                .orElse(certifications.isEmpty() ? null : certifications.getFirst());
        if (trigger != null && "PENDING_DEACTIVATION".equals(certificationFocus)) {
            return new Focus("CERTIFICATION", "CRITICAL", trigger.label() + " requiere atención prioritaria",
                    "Existe un segundo intento no aprobado. Consulta el detalle de tu certificación y prepárate con el contenido disponible.",
                    List.of(new ActionLink("STUDY", "Estudiar", "/student/study"),
                            new ActionLink("PRACTICE", "Practicar", "/student/study"),
                            new ActionLink("CERTIFICATION", "Ver certificación", "/student/certifications")));
        }
        if (trigger != null && ("EXPIRED".equals(certificationFocus) || "EXPIRING_SOON".equals(certificationFocus))) {
            String date = trigger.expirationDate() == null ? "" : " Vence el " + trigger.expirationDate() + ".";
            return new Focus("CERTIFICATION", "EXPIRED".equals(certificationFocus) ? "CRITICAL" : "ATTENTION",
                    trigger.label() + ("EXPIRED".equals(certificationFocus) ? " está vencida" : " está próxima a vencer"),
                    ("EXPIRED".equals(certificationFocus) ? "Esta certificación requiere tu atención." : "Conviene reforzar el contenido antes de tu siguiente aplicación.") + date,
                    List.of(new ActionLink("STUDY", "Estudiar", "/student/study"),
                            new ActionLink("PRACTICE", "Practicar", "/student/study"),
                            new ActionLink("CERTIFICATION", "Ver certificación", "/student/certifications")));
        }
        EvaluationCard evaluation = evaluations.stream().filter(item -> !"COMPLETED".equals(item.status())).findFirst().orElse(null);
        if (evaluation != null) {
            return new Focus("EVALUATION", "ACTION", "Tienes una evaluación pendiente", evaluation.title(),
                    List.of(new ActionLink("PREPARE", "Prepararme", "/student/study"),
                            new ActionLink("EVALUATION", evaluation.activeAttemptPublicId() == null ? "Comenzar" : "Continuar",
                                    evaluation.activeAttemptPublicId() == null ? "/student/evaluations" : "/student/evaluations/attempt/" + evaluation.activeAttemptPublicId())));
        }
        PathCard path = paths.stream().filter(item -> "IN_PROGRESS".equals(item.status())).findFirst()
                .orElse(paths.stream().filter(item -> "ASSIGNED".equals(item.status())).findFirst().orElse(null));
        if (path != null) {
            String detail = path.nextStageTitle() == null ? "Tu ruta de desarrollo está disponible."
                    : "Tu siguiente etapa es " + path.nextStageTitle() + ".";
            return new Focus("PATH", "ACTION", "Continúa " + path.name(), detail,
                    List.of(new ActionLink("PATH", "Continuar Path", "/student/paths/" + path.assignmentPublicId())));
        }
        if (!improvement.isEmpty()) {
            PreparationPoint item = improvement.getFirst();
            return new Focus("STUDY", "ACTION", "Te conviene reforzar " + item.label(),
                    "Tus prácticas recientes muestran una oportunidad de mejora en este tema.",
                    List.of(new ActionLink("PRACTICE", "Practicar", "/student/study")));
        }
        return new Focus("LEARNING", "OK", "Tus pendientes están bajo control",
                "Puedes continuar avanzando con una práctica o explorar un tema para reforzar.",
                List.of(new ActionLink("PRACTICE", "Práctica rápida", "/student/study")));
    }

    private ContinueItem evaluationContinue(List<EvaluationCard> evaluations) {
        return evaluations.stream().filter(item -> item.activeAttemptPublicId() != null && item.canResume()).findFirst()
                .map(item -> new ContinueItem("EVALUATION", item.title(), "Evaluación en progreso", null,
                        item.questionCount(), null, "/student/evaluations/attempt/" + item.activeAttemptPublicId())).orElse(null);
    }

    private ContinueItem pathContinue(List<PathCard> paths) {
        return paths.stream().filter(item -> "IN_PROGRESS".equals(item.status())).findFirst()
                .map(item -> new ContinueItem("PATH", item.name(),
                        item.nextStageTitle() == null ? "Path en progreso" : "Siguiente: " + item.nextStageTitle(),
                        item.completedStages(), item.totalStages(), null,
                        "/student/paths/" + item.assignmentPublicId())).orElse(null);
    }

    private List<Recommendation> recommendations(String certificationFocus, List<CertificationCard> certifications,
            List<EvaluationCard> evaluations, List<PathCard> paths, List<PreparationPoint> improvement) {
        List<Recommendation> result = new ArrayList<>();
        if (certificationFocus != null && !"IN_RULE".equals(certificationFocus)) {
            CertificationCard item = certifications.stream().filter(value -> value.status().equals(certificationFocus)).findFirst().orElse(null);
            if (item != null) result.add(new Recommendation("CERTIFICATION",
                    "PENDING_DEACTIVATION".equals(certificationFocus) || "EXPIRED".equals(certificationFocus) ? "CRITICAL" : "ATTENTION",
                    item.label(), "Revisa tu situación de certificación y utiliza las prácticas disponibles para prepararte.",
                    "Ver certificaciones", "/student/certifications"));
        }
        evaluations.stream().filter(item -> !"COMPLETED".equals(item.status())).limit(2).forEach(item -> result.add(
                new Recommendation("EVALUATION", "ACTION", item.title(), "Tienes esta evaluación pendiente.",
                        "Ver evaluación", "/student/evaluations")));
        paths.stream().filter(item -> !"COMPLETED".equals(item.status()) && !"EMPTY".equals(item.status())).limit(1)
                .forEach(item -> result.add(new Recommendation("PATH", "ACTION", "Continúa " + item.name(),
                        item.nextStageTitle() == null ? "Tu Path está disponible." : "Siguiente etapa: " + item.nextStageTitle() + ".",
                        "Continuar Path", "/student/paths/" + item.assignmentPublicId())));
        improvement.stream().limit(2).forEach(item -> result.add(new Recommendation("STUDY", "ACTION",
                "Refuerza " + item.label(), "Tu desempeño reciente en este tema es de " + item.percentage() + "%.",
                "Practicar", "/student/study")));
        return result.stream().limit(4).toList();
    }

    private List<ActivityItem> certificationActivity(AuthenticatedStudent student) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT cycle.UPDATED_AT OCCURRED_AT, 'CERTIFICATION_UPDATED' EVENT_TYPE,
                           'Se actualizó tu certificación de ' ||
                           CASE cycle.CERTIFICATION_TYPE WHEN 'TECHNOLOGICAL' THEN 'Tecnológica'
                             WHEN 'DEVELOPMENT_SECURITY' THEN 'Desarrollo Seguro'
                             WHEN 'NORMATIVE_TESTING' THEN 'Normativa y Testing'
                             WHEN 'ONE' THEN 'ONE' WHEN 'AGILE' THEN 'Agile' WHEN 'JIRA' THEN 'Jira' ELSE cycle.CERTIFICATION_TYPE END TITLE,
                           CASE cycle.VALIDITY_STATUS WHEN 'VALID' THEN 'En regla' WHEN 'EXPIRING_SOON' THEN 'Próxima a vencer'
                             WHEN 'EXPIRED' THEN 'Vencida' ELSE 'Seguimiento actualizado' END DESCRIPTION
                      FROM STUDENT_CERTIFICATION_CYCLE cycle
                     WHERE cycle.STUDENT_ID=:studentId AND cycle.ORGANIZATION_ID=:organizationId AND cycle.ACTIVE=1
                     ORDER BY cycle.UPDATED_AT DESC
                ) WHERE ROWNUM <= 5
                """, Map.of("studentId", student.internalId(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new ActivityItem(rs.getString("EVENT_TYPE"), timestamp(rs.getTimestamp("OCCURRED_AT")),
                        rs.getString("TITLE"), rs.getString("DESCRIPTION")));
    }

    private static String certificationLabel(String value) { return switch (value) {
        case "TECHNOLOGICAL" -> "Tecnológica"; case "DEVELOPMENT_SECURITY" -> "Desarrollo Seguro";
        case "NORMATIVE_TESTING" -> "Normativa y Testing"; case "ONE" -> "ONE"; case "AGILE" -> "Agile";
        case "JIRA" -> "Jira"; default -> value;
    }; }
    private static String statusLabel(String value) { return switch (value) {
        case "PENDING_DEACTIVATION" -> "Pendiente de Baja"; case "EXPIRED" -> "Vencida";
        case "EXPIRING_SOON" -> "Próxima a vencer"; default -> "En regla";
    }; }
    private static LocalDate localDate(Date value) { return value == null ? null : value.toLocalDate(); }
    private static Instant timestamp(Timestamp value) { return value == null ? Instant.EPOCH : value.toInstant(); }
}
