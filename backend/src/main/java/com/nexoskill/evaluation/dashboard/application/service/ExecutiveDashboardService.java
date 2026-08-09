package com.nexoskill.evaluation.dashboard.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.certifications.application.CertificationModels.Applicability;
import com.nexoskill.evaluation.certifications.application.CertificationModels.AreaSummary;
import com.nexoskill.evaluation.certifications.application.CertificationModels.CycleView;
import com.nexoskill.evaluation.certifications.application.CertificationSummaryCalculator;
import com.nexoskill.evaluation.certifications.domain.CertificationExamStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationLevel;
import com.nexoskill.evaluation.certifications.domain.CertificationProcessType;
import com.nexoskill.evaluation.certifications.domain.CertificationTrackingStatus;
import com.nexoskill.evaluation.certifications.domain.CertificationType;
import com.nexoskill.evaluation.certifications.domain.CertificationValidityStatus;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.AttentionItem;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.ChartPoint;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.ComponentDefinition;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.ComponentPreference;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Configuration;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Filter;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.FilterOptions;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Kpis;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Option;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.OrganizationPoint;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Overview;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.SaveConfigurationCommand;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.Scope;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModels.CertificationTypePoint;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutiveDashboardService {
    private static final int ORACLE_IN_LIMIT = 900;
    private static final Set<String> COLLABORATOR_STATUSES = Set.of("ACTIVE", "INACTIVE", "ALL");
    private static final Set<String> CERTIFICATION_STATES = Set.of("VALID", "EXPIRING_SOON", "EXPIRED", "PENDING");
    private static final List<ComponentDefinition> CATALOG = List.of(
            component("KPI_ACTIVE_COLLABORATORS", "Colaboradores activos", "KPI", "SMALL"),
            component("KPI_TALENT_BANK", "Talent Bank", "KPI", "SMALL"),
            component("KPI_COMPLIANCE", "Cumplimiento de certificaciones", "KPI", "SMALL"),
            component("KPI_EXPIRING", "Próximas a vencer", "KPI", "SMALL"),
            component("KPI_EXPIRED", "Vencidas", "KPI", "SMALL"),
            component("KPI_RECERTIFICATION", "Recertificaciones pendientes", "KPI", "SMALL"),
            component("CHART_CERTIFICATION_STATUS", "Estado general de certificaciones", "GRAPH", "MEDIUM"),
            component("CHART_EXPIRATIONS", "Próximos vencimientos", "GRAPH", "MEDIUM"),
            component("CHART_TECHNOLOGIES", "Distribución por Tecnología", "GRAPH", "MEDIUM"),
            component("CHART_ROLES", "Distribución por Rol", "GRAPH", "MEDIUM"),
            component("CHART_CERTIFICATION_TYPES", "Estado por tipo de certificación", "GRAPH", "MEDIUM"),
            component("CHART_TALENT_BANK", "Composición de Talent Bank", "GRAPH", "MEDIUM"),
            new ComponentDefinition("CHART_ORGANIZATIONS", "Comparativo de organizaciones", "GRAPH",
                    List.of("MEDIUM", "LARGE"), "LARGE", true),
            component("ATTENTION_REQUIRED", "Atención requerida", "ATTENTION", "LARGE"));

    private static final List<ComponentPreference> DEFAULT_COMPONENTS = List.of(
            pref("KPI_ACTIVE_COLLABORATORS", "SMALL", 0), pref("KPI_TALENT_BANK", "SMALL", 1),
            pref("KPI_COMPLIANCE", "SMALL", 2), pref("KPI_EXPIRING", "SMALL", 3),
            pref("KPI_EXPIRED", "SMALL", 4), pref("KPI_RECERTIFICATION", "SMALL", 5),
            pref("CHART_CERTIFICATION_STATUS", "MEDIUM", 6), pref("CHART_EXPIRATIONS", "MEDIUM", 7),
            pref("CHART_TECHNOLOGIES", "MEDIUM", 8), pref("CHART_ROLES", "MEDIUM", 9),
            pref("CHART_CERTIFICATION_TYPES", "MEDIUM", 10), pref("CHART_TALENT_BANK", "MEDIUM", 11),
            pref("ATTENTION_REQUIRED", "LARGE", 12));

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExecutiveDashboardService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(TenantContext tenant, AuthenticatedUser actor, Filter requested) {
        boolean administrator = isAdministrator(actor);
        Filter filter = normalizeFilter(requested);
        EffectiveScope effective = resolveScope(tenant, administrator, filter.organizationPublicId());

        List<Collaborator> base = collaborators(effective, filter.collaboratorStatus());
        Map<Long, List<Technology>> technologies = technologies(base.stream().map(Collaborator::id).toList());
        List<Collaborator> selected = base.stream()
                .filter(item -> filter.role() == null || role(item).equalsIgnoreCase(filter.role()))
                .filter(item -> filter.technology() == null || technologies.getOrDefault(item.id(), List.of()).stream()
                        .anyMatch(technology -> technology.key().equalsIgnoreCase(filter.technology())))
                .toList();

        Map<Long, List<CycleView>> cycles = cycles(selected.stream().map(Collaborator::id).toList());
        Map<Long, List<AreaSummary>> summaries = selected.stream().collect(Collectors.toMap(Collaborator::id,
                item -> CertificationSummaryCalculator.summarize(item.applicability(), cycles.get(item.id()))));

        if (filter.certificationState() != null) {
            CertificationType type = certificationType(filter.certificationType());
            selected = selected.stream().filter(item -> summaries.getOrDefault(item.id(), List.of()).stream()
                    .filter(area -> type == null || area.type() == type)
                    .anyMatch(area -> area.executiveState().equals(filter.certificationState()))).toList();
        }

        Set<Long> selectedIds = selected.stream().map(Collaborator::id).collect(Collectors.toSet());
        List<AreaSummary> certificationAreas = selected.stream()
                .flatMap(item -> summaries.getOrDefault(item.id(), List.of()).stream())
                .filter(area -> filter.certificationType() == null || area.type().name().equals(filter.certificationType()))
                .filter(area -> filter.certificationState() == null || area.executiveState().equals(filter.certificationState()))
                .toList();

        List<TalentRow> talentRows = talents(effective, filter.role(), filter.technology());
        long active = selected.stream().filter(Collaborator::active).count();
        long valid = certificationAreas.stream().filter(AreaSummary::valid).count();
        long expiring = certificationAreas.stream().filter(AreaSummary::expiringSoon).count();
        long expired = certificationAreas.stream().filter(AreaSummary::expired).count();
        long recert = certificationAreas.stream().filter(AreaSummary::pendingRecertification).count();
        Double compliance = certificationAreas.isEmpty() ? null
                : Math.round((valid * 10000.0) / certificationAreas.size()) / 100.0;

        Kpis kpis = new Kpis(active, talentRows.size(), compliance, certificationAreas.size(), (int) valid,
                (int) expiring, (int) expired, (int) recert);
        List<ChartPoint> certificationStatus = certificationStatus(certificationAreas);
        List<CertificationTypePoint> certificationTypes = certificationTypes(selected, summaries);
        List<ChartPoint> expirations = expirations(certificationAreas);
        List<ChartPoint> technologyChart = countTechnologies(technologies, selectedIds);
        List<ChartPoint> roleChart = countLabels(selected.stream().map(ExecutiveDashboardService::role).toList());
        List<ChartPoint> talentChart = talentComposition(talentRows);
        List<OrganizationPoint> organizationChart = administrator
                ? organizationComparison(selected, summaries, talentRows, filter) : List.of();
        List<AttentionItem> attention = attention(expiring, expired, recert);

        FilterOptions options = filterOptions(effective, administrator);
        return new Overview(Instant.now(clock), scope(effective, administrator), canPersonalize(actor), filter,
                options, kpis, certificationStatus, certificationTypes, expirations, technologyChart, roleChart,
                talentChart, organizationChart, attention);
    }

    @Transactional(readOnly = true)
    public Configuration configuration(AuthenticatedUser actor) {
        boolean administrator = isAdministrator(actor);
        boolean canPersonalize = canPersonalize(actor);
        List<ComponentDefinition> catalog = catalog(administrator);
        if (!canPersonalize) {
            return new Configuration(false, false, defaults(administrator), catalog);
        }
        List<String> rows = jdbc.query("SELECT CONFIGURATION_JSON FROM USER_DASHBOARD_PREFERENCE WHERE USER_ID = :userId",
                Map.of("userId", actor.internalId()), (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty()) return new Configuration(false, true, defaults(administrator), catalog);
        try {
            List<ComponentPreference> parsed = objectMapper.readValue(rows.getFirst(), new TypeReference<>() {});
            return new Configuration(true, true, sanitize(parsed, administrator), catalog);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("DASHBOARD_CONFIGURATION_INVALID",
                    "La configuración guardada del Dashboard no es válida. Restablece el Dashboard para continuar.");
        }
    }

    @Transactional
    public Configuration saveConfiguration(AuthenticatedUser actor, SaveConfigurationCommand command) {
        if (!canPersonalize(actor)) {
            throw new BusinessException("DASHBOARD_PERSONALIZE_FORBIDDEN",
                    "No tienes permiso para personalizar el Dashboard.");
        }
        boolean administrator = isAdministrator(actor);
        List<ComponentPreference> components = sanitize(command == null ? null : command.components(), administrator);
        final String json;
        try {
            json = objectMapper.writeValueAsString(components);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("DASHBOARD_CONFIGURATION_INVALID", "No fue posible guardar la configuración del Dashboard.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("userId", actor.internalId()).addValue("configuration", json);
        jdbc.update("""
            MERGE INTO USER_DASHBOARD_PREFERENCE target
            USING (SELECT :userId USER_ID FROM DUAL) source
               ON (target.USER_ID = source.USER_ID)
            WHEN MATCHED THEN UPDATE SET
                target.CONFIGURATION_JSON = :configuration,
                target.UPDATED_AT = SYSTIMESTAMP,
                target.VERSION_NO = target.VERSION_NO + 1
            WHEN NOT MATCHED THEN INSERT (USER_ID, CONFIGURATION_JSON, CREATED_AT, UPDATED_AT, VERSION_NO)
                VALUES (:userId, :configuration, SYSTIMESTAMP, SYSTIMESTAMP, 0)
            """, params);
        return new Configuration(true, true, components, catalog(administrator));
    }

    private List<Collaborator> collaborators(EffectiveScope scope, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("""
             WHERE s.RECORD_MODULE = 'COLLABORATOR'
               AND s.STATUS <> 'DELETED'
               AND o.ORGANIZATION_TYPE = 'CUSTOMER'
            """);
        appendScope(where, params, scope, "s.ORGANIZATION_ID");
        if ("ACTIVE".equals(status)) {
            where.append(" AND s.STATUS = 'ACTIVE' AND s.ADMISSION_DATE IS NOT NULL ");
        } else if ("INACTIVE".equals(status)) {
            where.append(" AND (s.STATUS IN ('INACTIVE','EXPIRED') OR s.ADMISSION_DATE IS NULL) ");
        }
        return jdbc.query("""
            SELECT s.STUDENT_ID, s.ORGANIZATION_ID, o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_NAME,
                   s.STATUS, s.ADMISSION_DATE, p.PROFILE_NAME, tp.PROFILE_NAME TECH_PROFILE_NAME,
                   s.APPLIES_TECH_CERT, s.APPLIES_DEV_SECURITY, s.APPLIES_NORMATIVE_TESTING,
                   s.APPLIES_ONE, s.APPLIES_AGILE, s.APPLIES_JIRA
              FROM STUDENT s
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
              LEFT JOIN CERTIFICATION_PROFILE_CATALOG p ON p.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
              LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tp ON tp.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
            """ + where, params, (rs, rowNum) -> new Collaborator(rs.getLong("STUDENT_ID"),
                rs.getLong("ORGANIZATION_ID"), rs.getString("ORGANIZATION_PUBLIC_ID"), rs.getString("ORGANIZATION_NAME"),
                "ACTIVE".equals(rs.getString("STATUS")) && rs.getDate("ADMISSION_DATE") != null,
                rs.getString("PROFILE_NAME"), rs.getString("TECH_PROFILE_NAME"),
                new Applicability(rs.getBoolean("APPLIES_TECH_CERT"), rs.getBoolean("APPLIES_DEV_SECURITY"),
                        rs.getBoolean("APPLIES_NORMATIVE_TESTING"), rs.getBoolean("APPLIES_ONE"),
                        rs.getBoolean("APPLIES_AGILE"), rs.getBoolean("APPLIES_JIRA"))));
    }

    private Map<Long, List<Technology>> technologies(List<Long> studentIds) {
        Map<Long, List<Technology>> result = new LinkedHashMap<>();
        for (List<Long> batch : batches(studentIds)) {
            jdbc.query("""
                SELECT STUDENT_ID, NORMALIZED_NAME, ITEM_NAME
                  FROM STUDENT_EXPERIENCE_ITEM
                 WHERE ITEM_TYPE = 'CURRENT_TECHNOLOGY' AND STUDENT_ID IN (:ids)
                 ORDER BY STUDENT_ID, DISPLAY_ORDER, LOWER(ITEM_NAME)
                """, Map.of("ids", batch), rs -> {
                result.computeIfAbsent(rs.getLong("STUDENT_ID"), ignored -> new ArrayList<>())
                        .add(new Technology(rs.getString("NORMALIZED_NAME"), rs.getString("ITEM_NAME")));
            });
        }
        return result;
    }

    private Map<Long, List<CycleView>> cycles(List<Long> studentIds) {
        Map<Long, List<CycleView>> result = new LinkedHashMap<>();
        for (List<Long> batch : batches(studentIds)) {
            jdbc.query("""
                SELECT c.STUDENT_ID, c.PUBLIC_ID, c.CERTIFICATION_TYPE, t.PUBLIC_ID TECHNOLOGY_PUBLIC_ID,
                       t.TECHNOLOGY_NAME, c.CERTIFICATION_LEVEL, c.IS_PRIMARY, c.PROCESS_TYPE,
                       c.TRACKING_STATUS, c.DEADLINE_DATE, c.SCHEDULED_DATE, c.APPLICATION_DATE,
                       c.LAST_APPROVED_APPLICATION_DATE, c.APPROVED, c.EXPIRATION_DATE, c.VALIDITY_STATUS,
                       previous.PUBLIC_ID PREVIOUS_PUBLIC_ID, c.ACTIONS_TO_TAKE, c.SOFTTEK_MANAGEMENT,
                       c.OBSERVATIONS, c.ACTIVE, c.LATEST_SCORE, c.LATEST_EXAM_STATUS,
                       c.IMPORTED_FAILURE_COUNT, c.RESULT_SOURCE, c.VERSION_NO
                  FROM STUDENT_CERTIFICATION_CYCLE c
                  LEFT JOIN QUESTION_TECHNOLOGY t ON t.TECHNOLOGY_ID = c.TECHNOLOGY_ID
                  LEFT JOIN STUDENT_CERTIFICATION_CYCLE previous
                    ON previous.STUDENT_CERTIFICATION_CYCLE_ID = c.PREVIOUS_APPROVED_CYCLE_ID
                 WHERE c.STUDENT_ID IN (:ids)
                 ORDER BY c.STUDENT_ID, c.CERTIFICATION_TYPE, c.IS_PRIMARY DESC, c.CREATED_AT DESC
                """, Map.of("ids", batch), rs -> {
                    result.computeIfAbsent(rs.getLong("STUDENT_ID"), ignored -> new ArrayList<>()).add(mapCycle(rs));
                });
        }
        return result;
    }

    private List<TalentRow> talents(EffectiveScope scope, String role, String technology) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("""
             WHERE s.RECORD_MODULE = 'TALENT_BANK' AND s.STATUS <> 'DELETED'
               AND o.ORGANIZATION_TYPE = 'CUSTOMER'
            """);
        appendScope(where, params, scope, "s.ORGANIZATION_ID");
        List<TalentRow> rows = jdbc.query("""
            SELECT s.STUDENT_ID, s.ORGANIZATION_ID, o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_NAME,
                   s.TALENT_TYPE, p.PROFILE_NAME, tp.PROFILE_NAME TECH_PROFILE_NAME,
                   talent_tech.TECHNOLOGY_NAME TALENT_TECH_NAME
              FROM STUDENT s
              JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
              LEFT JOIN CERTIFICATION_PROFILE_CATALOG p ON p.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
              LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tp ON tp.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
              LEFT JOIN QUESTION_TECHNOLOGY talent_tech ON talent_tech.TECHNOLOGY_ID = s.TALENT_TECHNOLOGY_ID
            """ + where, params, (rs, rowNum) -> new TalentRow(rs.getLong("STUDENT_ID"), rs.getLong("ORGANIZATION_ID"),
                rs.getString("ORGANIZATION_PUBLIC_ID"), rs.getString("ORGANIZATION_NAME"), rs.getString("TALENT_TYPE"),
                rs.getString("PROFILE_NAME"), rs.getString("TECH_PROFILE_NAME"), normalizeTechnologyKey(rs.getString("TALENT_TECH_NAME")),
                rs.getString("TALENT_TECH_NAME")));
        Map<Long, List<Technology>> current = technologies(rows.stream().map(TalentRow::id).toList());
        return rows.stream().filter(item -> role == null || role(item.profile(), item.techProfile()).equalsIgnoreCase(role))
                .filter(item -> technology == null
                        || (item.technologyKey() != null && item.technologyKey().equalsIgnoreCase(technology))
                        || current.getOrDefault(item.id(), List.of()).stream()
                                .anyMatch(value -> value.key().equalsIgnoreCase(technology)))
                .toList();
    }

    private FilterOptions filterOptions(EffectiveScope scope, boolean administrator) {
        List<Collaborator> all = collaborators(scope, "ALL");
        Map<Long, List<Technology>> technologyMap = technologies(all.stream().map(Collaborator::id).toList());
        List<Option> organizations = administrator ? organizations() : List.of();
        List<Option> roles = all.stream().map(ExecutiveDashboardService::role).filter(value -> !value.isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).map(value -> new Option(value, value)).toList();
        Map<String, String> technologies = new LinkedHashMap<>();
        technologyMap.values().stream().flatMap(List::stream).sorted(Comparator.comparing(Technology::label,
                String.CASE_INSENSITIVE_ORDER)).forEach(value -> technologies.putIfAbsent(value.key(), value.label()));
        List<Option> technologyOptions = technologies.entrySet().stream().map(entry -> new Option(entry.getKey(), entry.getValue())).toList();
        return new FilterOptions(organizations, roles, technologyOptions,
                List.of(new Option("ACTIVE", "Activos"), new Option("INACTIVE", "Inactivos"), new Option("ALL", "Todos")),
                List.of(new Option("TECHNOLOGICAL", "Tecnológica"), new Option("DEVELOPMENT_SECURITY", "Desarrollo Seguro"),
                        new Option("NORMATIVE_TESTING", "Normativa y Testing"), new Option("ONE", "ONE"),
                        new Option("AGILE", "Agile"), new Option("JIRA", "Jira")),
                List.of(new Option("VALID", "Vigentes"), new Option("EXPIRING_SOON", "Próximas a vencer"),
                        new Option("EXPIRED", "Vencidas"), new Option("PENDING", "Pendientes")));
    }

    private List<Option> organizations() {
        LocalDate today = LocalDate.now(clock);
        return jdbc.query("""
            SELECT PUBLIC_ID, ORGANIZATION_NAME FROM ORGANIZATION
             WHERE ORGANIZATION_TYPE = 'CUSTOMER' AND STATUS = 'ACTIVE'
               AND (VALID_FROM IS NULL OR VALID_FROM <= :today)
               AND (EXPIRES_ON IS NULL OR EXPIRES_ON >= :today)
             ORDER BY LOWER(ORGANIZATION_NAME)
            """, Map.of("today", java.sql.Date.valueOf(today)),
                (rs, rowNum) -> new Option(rs.getString(1), rs.getString(2)));
    }

    private EffectiveScope resolveScope(TenantContext tenant, boolean administrator, String requestedOrganization) {
        if (!administrator) {
            if (requestedOrganization != null) {
                throw new BusinessException("DASHBOARD_ORGANIZATION_FORBIDDEN",
                        "Tu rol no puede cambiar la organización del Dashboard.");
            }
            if (tenant == null || tenant.organizationId() == null || tenant.globalScope()) {
                throw new BusinessException("DASHBOARD_SCOPE_INVALID", "No existe una organización válida para consultar el Dashboard.");
            }
            List<String> names = jdbc.query("""
                SELECT ORGANIZATION_NAME FROM ORGANIZATION
                 WHERE ORGANIZATION_ID = :organizationId AND ORGANIZATION_TYPE = 'CUSTOMER'
                """, Map.of("organizationId", tenant.organizationId()), (rs, rowNum) -> rs.getString(1));
            if (names.isEmpty()) {
                throw new BusinessException("DASHBOARD_SCOPE_INVALID",
                        "La organización asignada no está disponible para consultar el Dashboard.");
            }
            return new EffectiveScope(tenant.organizationId(), tenant.organizationPublicId(), names.getFirst(), false);
        }
        if (requestedOrganization == null) return new EffectiveScope(null, null, "Todas las organizaciones", true);
        LocalDate today = LocalDate.now(clock);
        List<EffectiveScope> rows = jdbc.query("""
            SELECT ORGANIZATION_ID, PUBLIC_ID, ORGANIZATION_NAME
              FROM ORGANIZATION
             WHERE PUBLIC_ID = :publicId AND ORGANIZATION_TYPE = 'CUSTOMER' AND STATUS = 'ACTIVE'
               AND (VALID_FROM IS NULL OR VALID_FROM <= :today)
               AND (EXPIRES_ON IS NULL OR EXPIRES_ON >= :today)
            """, Map.of("publicId", requestedOrganization, "today", java.sql.Date.valueOf(today)),
                (rs, rowNum) -> new EffectiveScope(rs.getLong(1), rs.getString(2), rs.getString(3), false));
        if (rows.isEmpty()) throw new BusinessException("DASHBOARD_ORGANIZATION_INVALID",
                "La organización seleccionada no está disponible para consulta.");
        return rows.getFirst();
    }

    private static Scope scope(EffectiveScope scope, boolean administrator) {
        return new Scope(administrator, scope.global(), scope.publicId(), scope.name());
    }

    private static Filter normalizeFilter(Filter requested) {
        Filter source = requested == null ? new Filter(null, null, null, null, null, null) : requested;
        String status = cleanUpper(source.collaboratorStatus());
        if (status == null) status = "ACTIVE";
        if (!COLLABORATOR_STATUSES.contains(status)) throw new BusinessException("DASHBOARD_STATUS_INVALID", "El estado solicitado no es válido.");
        String certType = cleanUpper(source.certificationType());
        if (certType != null) certificationType(certType);
        String certState = cleanUpper(source.certificationState());
        if (certState != null && !CERTIFICATION_STATES.contains(certState)) {
            throw new BusinessException("DASHBOARD_CERTIFICATION_STATE_INVALID", "El estado de certificación solicitado no es válido.");
        }
        return new Filter(clean(source.organizationPublicId()), clean(source.role()), clean(source.technology()),
                status, certType, certState);
    }

    private static CertificationType certificationType(String value) {
        if (value == null) return null;
        try {
            return CertificationType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("DASHBOARD_CERTIFICATION_TYPE_INVALID", "El tipo de certificación solicitado no es válido.");
        }
    }

    private static List<ChartPoint> certificationStatus(List<AreaSummary> areas) {
        Map<String, Long> counts = areas.stream().collect(Collectors.groupingBy(AreaSummary::executiveState,
                LinkedHashMap::new, Collectors.counting()));
        return List.of(new ChartPoint("VALID", "Vigentes", counts.getOrDefault("VALID", 0L)),
                new ChartPoint("EXPIRING_SOON", "Próximas a vencer", counts.getOrDefault("EXPIRING_SOON", 0L)),
                new ChartPoint("EXPIRED", "Vencidas", counts.getOrDefault("EXPIRED", 0L)),
                new ChartPoint("PENDING", "Pendientes", counts.getOrDefault("PENDING", 0L)));
    }

    private static List<CertificationTypePoint> certificationTypes(List<Collaborator> collaborators,
            Map<Long, List<AreaSummary>> summaries) {
        List<CertificationTypePoint> result = new ArrayList<>();
        for (CertificationType type : CertificationType.values()) {
            List<AreaSummary> areas = collaborators.stream().flatMap(item -> summaries.getOrDefault(item.id(), List.of()).stream())
                    .filter(area -> area.type() == type).toList();
            long covered = areas.stream().filter(AreaSummary::valid).count();
            long pending = areas.stream().filter(area -> "PENDING".equals(area.executiveState())).count();
            long expiring = areas.stream().filter(AreaSummary::expiringSoon).count();
            long expired = areas.stream().filter(AreaSummary::expired).count();
            Double compliance = areas.isEmpty() ? null : Math.round(covered * 10000.0 / areas.size()) / 100.0;
            result.add(new CertificationTypePoint(type.name(), certificationLabel(type), areas.size(), covered,
                    pending, expiring, expired, compliance));
        }
        return result;
    }

    private List<ChartPoint> expirations(List<AreaSummary> areas) {
        YearMonth start = YearMonth.from(LocalDate.now(clock));
        Map<YearMonth, Long> counts = areas.stream().flatMap(area -> area.expirationDates().stream())
                .map(YearMonth::from).filter(month -> !month.isBefore(start) && month.isBefore(start.plusMonths(12)))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<ChartPoint> result = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            YearMonth month = start.plusMonths(index);
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es-MX"));
            label = Character.toUpperCase(label.charAt(0)) + label.substring(1).replace(".", "") + " " + month.getYear();
            result.add(new ChartPoint(month.toString(), label, counts.getOrDefault(month, 0L)));
        }
        return result;
    }

    private static List<ChartPoint> countTechnologies(Map<Long, List<Technology>> technologies, Set<Long> selectedIds) {
        Map<String, Counter> counts = new LinkedHashMap<>();
        selectedIds.forEach(id -> technologies.getOrDefault(id, List.of()).forEach(value ->
                counts.computeIfAbsent(value.key(), ignored -> new Counter(value.label())).increment()));
        return counts.entrySet().stream().map(entry -> new ChartPoint(entry.getKey(), entry.getValue().label, entry.getValue().value))
                .sorted(Comparator.comparingLong(ChartPoint::value).reversed().thenComparing(ChartPoint::label)).toList();
    }

    private static List<ChartPoint> countLabels(List<String> labels) {
        Map<String, Long> counts = labels.stream().filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(entry -> new ChartPoint(entry.getKey(), entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(ChartPoint::value).reversed().thenComparing(ChartPoint::label)).toList();
    }

    private static List<ChartPoint> talentComposition(List<TalentRow> rows) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(TalentRow::type, Collectors.counting()));
        return List.of(new ChartPoint("BBVA_EXIT", "Bajas", counts.getOrDefault("BBVA_EXIT", 0L)),
                new ChartPoint("ACADEMY", "Academia", counts.getOrDefault("ACADEMY", 0L)),
                new ChartPoint("PROSPECT", "Prospectos", counts.getOrDefault("PROSPECT", 0L)));
    }

    private static List<OrganizationPoint> organizationComparison(List<Collaborator> collaborators,
            Map<Long, List<AreaSummary>> summaries, List<TalentRow> talents, Filter filter) {
        Map<Long, OrgCounter> values = new LinkedHashMap<>();
        collaborators.forEach(item -> {
            OrgCounter counter = values.computeIfAbsent(item.organizationId(), ignored ->
                    new OrgCounter(item.organizationPublicId(), item.organizationName()));
            if (item.active()) counter.active++;
            summaries.getOrDefault(item.id(), List.of()).stream()
                    .filter(area -> filter.certificationType() == null || area.type().name().equals(filter.certificationType()))
                    .filter(area -> filter.certificationState() == null || area.executiveState().equals(filter.certificationState()))
                    .forEach(area -> {
                        switch (area.executiveState()) {
                            case "VALID" -> counter.valid++;
                            case "EXPIRING_SOON" -> counter.expiring++;
                            case "EXPIRED" -> counter.expired++;
                            default -> { }
                        }
                    });
        });
        talents.forEach(item -> values.computeIfAbsent(item.organizationId(), ignored ->
                new OrgCounter(item.organizationPublicId(), item.organizationName())).talent++);
        return values.values().stream().map(value -> new OrganizationPoint(value.publicId, value.name, value.active,
                value.talent, value.valid, value.expiring, value.expired))
                .sorted(Comparator.comparingLong(OrganizationPoint::activeCollaborators).reversed()
                        .thenComparing(OrganizationPoint::label)).toList();
    }

    private static List<AttentionItem> attention(long expiring, long expired, long recert) {
        return List.of(new AttentionItem("EXPIRED", "Certificaciones vencidas", expired, "CRITICAL",
                        "Requieren seguimiento conforme a las reglas vigentes."),
                new AttentionItem("EXPIRING_SOON", "Próximas a vencer", expiring, "WARNING",
                        "Conviene anticipar su gestión antes del vencimiento."),
                new AttentionItem("RECERTIFICATION", "Recertificaciones pendientes", recert, "ATTENTION",
                        "Áreas vencidas que requieren un nuevo ciclo de certificación."));
    }

    private List<ComponentPreference> sanitize(List<ComponentPreference> requested, boolean administrator) {
        if (requested == null) throw new BusinessException("DASHBOARD_CONFIGURATION_REQUIRED", "La configuración del Dashboard es obligatoria.");
        Map<String, ComponentDefinition> allowed = catalog(administrator).stream()
                .collect(Collectors.toMap(ComponentDefinition::code, Function.identity()));
        Set<String> seen = new LinkedHashSet<>();
        List<ComponentPreference> sorted = requested.stream().sorted(Comparator.comparingInt(ComponentPreference::order)).toList();
        List<ComponentPreference> result = new ArrayList<>();
        for (ComponentPreference item : sorted) {
            if (item == null || item.code() == null || !allowed.containsKey(item.code())) {
                throw new BusinessException("DASHBOARD_COMPONENT_INVALID", "La configuración contiene un componente no autorizado.");
            }
            if (!seen.add(item.code())) throw new BusinessException("DASHBOARD_COMPONENT_DUPLICATED", "Un componente no puede aparecer más de una vez.");
            ComponentDefinition definition = allowed.get(item.code());
            String size = cleanUpper(item.size());
            if (size == null) size = definition.defaultSize();
            if (!definition.allowedSizes().contains(size)) throw new BusinessException("DASHBOARD_COMPONENT_SIZE_INVALID", "El tamaño solicitado no es válido para " + definition.title() + ".");
            result.add(new ComponentPreference(item.code(), size, result.size()));
        }
        return List.copyOf(result);
    }

    private static List<ComponentDefinition> catalog(boolean administrator) {
        return CATALOG.stream().filter(item -> administrator || !item.administratorOnly()).toList();
    }

    private static List<ComponentPreference> defaults(boolean administrator) {
        return DEFAULT_COMPONENTS.stream().filter(item -> administrator || !"CHART_ORGANIZATIONS".equals(item.code())).toList();
    }

    private static boolean isAdministrator(AuthenticatedUser actor) {
        return actor != null && actor.roles().stream().anyMatch(role -> "ADMINISTRATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    private static boolean canPersonalize(AuthenticatedUser actor) {
        return isAdministrator(actor) || (actor != null
                && actor.permissions().contains("DASHBOARD_VIEW")
                && actor.permissions().contains("DASHBOARD_PERSONALIZE"));
    }

    private static void appendScope(StringBuilder where, MapSqlParameterSource params, EffectiveScope scope, String column) {
        if (scope.organizationId() != null) {
            where.append(" AND ").append(column).append(" = :organizationId ");
            params.addValue("organizationId", scope.organizationId());
        }
    }

    private CycleView mapCycle(ResultSet rs) throws SQLException {
        return new CycleView(rs.getString("PUBLIC_ID"), CertificationType.valueOf(rs.getString("CERTIFICATION_TYPE")),
                rs.getString("TECHNOLOGY_PUBLIC_ID"), rs.getString("TECHNOLOGY_NAME"),
                nullableEnum(CertificationLevel.class, rs.getString("CERTIFICATION_LEVEL")), rs.getBoolean("IS_PRIMARY"),
                CertificationProcessType.valueOf(rs.getString("PROCESS_TYPE")),
                CertificationTrackingStatus.valueOf(rs.getString("TRACKING_STATUS")), localDate(rs, "DEADLINE_DATE"),
                localDate(rs, "SCHEDULED_DATE"), localDate(rs, "APPLICATION_DATE"),
                localDate(rs, "LAST_APPROVED_APPLICATION_DATE"), nullableBoolean(rs, "APPROVED"),
                localDate(rs, "EXPIRATION_DATE"), CertificationValidityStatus.valueOf(rs.getString("VALIDITY_STATUS")),
                rs.getString("PREVIOUS_PUBLIC_ID"), rs.getString("ACTIONS_TO_TAKE"), rs.getString("SOFTTEK_MANAGEMENT"),
                rs.getString("OBSERVATIONS"), rs.getBoolean("ACTIVE"), rs.getBigDecimal("LATEST_SCORE"),
                CertificationExamStatus.valueOf(rs.getString("LATEST_EXAM_STATUS")), 0,
                nullableInteger(rs, "IMPORTED_FAILURE_COUNT"), rs.getString("RESULT_SOURCE"), rs.getLong("VERSION_NO"));
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value == 1;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static <E extends Enum<E>> E nullableEnum(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static <T> List<List<T>> batches(List<T> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<List<T>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index += ORACLE_IN_LIMIT) {
            result.add(values.subList(index, Math.min(index + ORACLE_IN_LIMIT, values.size())));
        }
        return result;
    }

    private static String role(Collaborator item) {
        return role(item.profile(), item.techProfile());
    }

    private static String role(String profile, String techProfile) {
        String left = clean(profile);
        String right = clean(techProfile);
        if (left == null) return right == null ? "Sin Rol" : right;
        if (right == null) return left;
        return left + " - " + right;
    }

    private static String certificationLabel(CertificationType type) {
        return switch (type) {
            case TECHNOLOGICAL -> "Tecnológica";
            case DEVELOPMENT_SECURITY -> "Desarrollo Seguro";
            case NORMATIVE_TESTING -> "Normativa y Testing";
            case ONE -> "ONE";
            case AGILE -> "Agile";
            case JIRA -> "Jira";
        };
    }

    private static ComponentDefinition component(String code, String title, String category, String defaultSize) {
        return new ComponentDefinition(code, title, category, List.of("SMALL", "MEDIUM", "LARGE"), defaultSize, false);
    }

    private static ComponentPreference pref(String code, String size, int order) {
        return new ComponentPreference(code, size, order);
    }

    private static String normalizeTechnologyKey(String value) {
        if (value == null) return null;
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cleanUpper(String value) {
        String clean = clean(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }

    private record EffectiveScope(Long organizationId, String publicId, String name, boolean global) {
    }

    private record Collaborator(Long id, Long organizationId, String organizationPublicId, String organizationName,
            boolean active, String profile, String techProfile, Applicability applicability) {
    }

    private record Technology(String key, String label) {
    }

    private record TalentRow(Long id, Long organizationId, String organizationPublicId, String organizationName,
            String type, String profile, String techProfile, String technologyKey, String technologyName) {
    }

    private static final class Counter {
        private final String label;
        private long value;

        private Counter(String label) { this.label = label; }
        private void increment() { value++; }
    }

    private static final class OrgCounter {
        private final String publicId;
        private final String name;
        private long active;
        private long talent;
        private long valid;
        private long expiring;
        private long expired;

        private OrgCounter(String publicId, String name) {
            this.publicId = publicId;
            this.name = name;
        }
    }
}
