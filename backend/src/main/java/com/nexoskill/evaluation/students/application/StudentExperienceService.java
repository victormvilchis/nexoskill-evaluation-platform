package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentExperienceService {
    private static final Set<String> TYPES = Set.of("CURRENT_TECHNOLOGY", "LANGUAGE", "KNOWN_TECHNOLOGY");
    private static final Set<String> LEVELS = Set.of("JR", "STD", "SR");
    private final NamedParameterJdbcTemplate jdbc;

    public StudentExperienceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ExperienceView get(TenantContext tenant, String studentPublicId) {
        StudentRef student = requireStudent(tenant, studentPublicId);
        List<ExperienceItem> items = jdbc.query("""
            SELECT PUBLIC_ID, ITEM_TYPE, ITEM_NAME, LEVEL_CODE, DISPLAY_ORDER
              FROM STUDENT_EXPERIENCE_ITEM
             WHERE STUDENT_ID = :studentId AND ORGANIZATION_ID = :organizationId
             ORDER BY ITEM_TYPE, DISPLAY_ORDER, LOWER(ITEM_NAME)
            """, Map.of("studentId", student.id(), "organizationId", student.organizationId()),
                (rs, rowNum) -> new ExperienceItem(rs.getString("PUBLIC_ID"), rs.getString("ITEM_TYPE"),
                        rs.getString("ITEM_NAME"), rs.getString("LEVEL_CODE"), rs.getInt("DISPLAY_ORDER")));
        return new ExperienceView(items.stream().filter(item -> "CURRENT_TECHNOLOGY".equals(item.type())).toList(),
                items.stream().filter(item -> "LANGUAGE".equals(item.type())).toList(),
                items.stream().filter(item -> "KNOWN_TECHNOLOGY".equals(item.type())).toList());
    }

    @Transactional
    public ExperienceView update(TenantContext tenant, String studentPublicId, UpdateCommand command, Long actorId) {
        if (actorId == null) {
            throw new BusinessException("STUDENT_ACTOR_REQUIRED", "No fue posible identificar al usuario.");
        }
        if (command == null) {
            throw new BusinessException("STUDENT_EXPERIENCE_REQUIRED", "La información de experiencia es obligatoria.");
        }
        StudentRef student = requireStudent(tenant, studentPublicId);
        replace(student, "CURRENT_TECHNOLOGY", command.currentTechnologies(), actorId);
        replace(student, "LANGUAGE", command.languages(), actorId);
        replace(student, "KNOWN_TECHNOLOGY", command.knownTechnologies(), actorId);
        return get(tenant, studentPublicId);
    }

    @Transactional
    public void replaceImported(Long organizationId, Long studentId,
            List<ImportedItem> currentTechnologies, List<ImportedItem> languages,
            List<ImportedItem> knownTechnologies, Long actorId) {
        if (organizationId == null || studentId == null || actorId == null) {
            throw new BusinessException("STUDENT_EXPERIENCE_CONTEXT_REQUIRED",
                    "No fue posible identificar al colaborador, la organización o el usuario.");
        }
        StudentRef student = new StudentRef(studentId, organizationId);
        replace(student, "CURRENT_TECHNOLOGY", toCommands(currentTechnologies), actorId);
        replace(student, "LANGUAGE", toCommands(languages), actorId);
        replace(student, "KNOWN_TECHNOLOGY", toCommands(knownTechnologies), actorId);
    }

    private List<ExperienceItemCommand> toCommands(List<ImportedItem> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> new ExperienceItemCommand(value.name(), value.level())).toList();
    }

    private void replace(StudentRef student, String type, List<ExperienceItemCommand> values, Long actorId) {
        if (!TYPES.contains(type)) {
            throw new BusinessException("STUDENT_EXPERIENCE_TYPE_INVALID", "El tipo de experiencia no es válido.");
        }
        List<ExperienceItemCommand> normalized = normalizeItems(values);
        jdbc.update("DELETE FROM STUDENT_EXPERIENCE_ITEM WHERE STUDENT_ID = :studentId AND ORGANIZATION_ID = :organizationId AND ITEM_TYPE = :type",
                Map.of("studentId", student.id(), "organizationId", student.organizationId(), "type", type));
        int order = 0;
        for (ExperienceItemCommand item : normalized) {
            jdbc.update("""
                INSERT INTO STUDENT_EXPERIENCE_ITEM
                    (PUBLIC_ID, STUDENT_ID, ORGANIZATION_ID, ITEM_TYPE, ITEM_NAME, NORMALIZED_NAME,
                     LEVEL_CODE, DISPLAY_ORDER, CREATED_BY, UPDATED_BY, CREATED_AT, UPDATED_AT, VERSION_NO)
                VALUES (:publicId, :studentId, :organizationId, :type, :name, :normalizedName,
                        :level, :displayOrder, :actorId, :actorId, SYSTIMESTAMP, SYSTIMESTAMP, 0)
                """, new MapSqlParameterSource()
                    .addValue("publicId", UUID.randomUUID().toString())
                    .addValue("studentId", student.id())
                    .addValue("organizationId", student.organizationId())
                    .addValue("type", type)
                    .addValue("name", item.name())
                    .addValue("normalizedName", normalizeKey(item.name()))
                    .addValue("level", item.level())
                    .addValue("displayOrder", order++)
                    .addValue("actorId", actorId));
        }
    }

    private List<ExperienceItemCommand> normalizeItems(List<ExperienceItemCommand> values) {
        if (values == null || values.isEmpty()) return List.of();
        Map<String, ExperienceItemCommand> unique = new LinkedHashMap<>();
        for (ExperienceItemCommand item : values) {
            if (item == null || item.name() == null || item.name().isBlank()) continue;
            String name = normalizeNullable(item.name(), 200);
            String key = normalizeKey(name);
            String level = item.level() == null || item.level().isBlank()
                    ? null : item.level().trim().toUpperCase(Locale.ROOT);
            if (level != null && !LEVELS.contains(level)) {
                throw new BusinessException("STUDENT_EXPERIENCE_LEVEL_INVALID",
                        "El nivel de experiencia debe ser JR, STD o SR.");
            }
            unique.putIfAbsent(key, new ExperienceItemCommand(name, level));
            if (unique.size() > 100) {
                throw new BusinessException("STUDENT_EXPERIENCE_LIMIT",
                        "No puedes registrar más de 100 elementos por sección.");
            }
        }
        return List.copyOf(unique.values());
    }

    private StudentRef requireStudent(TenantContext tenant, String publicId) {
        if (tenant == null || publicId == null || publicId.isBlank()) {
            throw new BusinessException("STUDENT_NOT_FOUND", "El colaborador no existe.");
        }
        String sql = """
            SELECT STUDENT_ID, ORGANIZATION_ID
              FROM STUDENT
             WHERE PUBLIC_ID = :publicId AND STATUS <> 'DELETED'
            """ + (tenant.globalAdministrator() ? "" : " AND ORGANIZATION_ID = :organizationId");
        MapSqlParameterSource params = new MapSqlParameterSource("publicId", publicId.trim());
        if (!tenant.globalAdministrator()) {
            if (!tenant.hasOrganization()) {
                throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "No existe un contexto autorizado.");
            }
            params.addValue("organizationId", tenant.organizationId());
        }
        List<StudentRef> rows = jdbc.query(sql, params, (rs, rowNum) ->
                new StudentRef(rs.getLong("STUDENT_ID"), rs.getLong("ORGANIZATION_ID")));
        if (rows.isEmpty()) throw new BusinessException("STUDENT_NOT_FOUND", "El colaborador no existe.");
        return rows.getFirst();
    }

    private static String normalizeNullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > max) {
            throw new BusinessException("STUDENT_EXPERIENCE_VALUE_INVALID",
                    "Un valor de experiencia supera la longitud permitida de " + max + " caracteres.");
        }
        return normalized;
    }

    public static String normalizeKey(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private record StudentRef(Long id, Long organizationId) {}
    public record ExperienceItem(String publicId, String type, String name, String level, int order) {}
    public record ExperienceView(List<ExperienceItem> currentTechnologies,
            List<ExperienceItem> languages, List<ExperienceItem> knownTechnologies) {
        public ExperienceView {
            currentTechnologies = currentTechnologies == null ? List.of() : List.copyOf(currentTechnologies);
            languages = languages == null ? List.of() : List.copyOf(languages);
            knownTechnologies = knownTechnologies == null ? List.of() : List.copyOf(knownTechnologies);
        }
    }
    public record ExperienceItemCommand(String name, String level) {}
    public record ImportedItem(String name, String level) {}
    public record UpdateCommand(List<ExperienceItemCommand> currentTechnologies,
            List<ExperienceItemCommand> languages, List<ExperienceItemCommand> knownTechnologies) {}
}
