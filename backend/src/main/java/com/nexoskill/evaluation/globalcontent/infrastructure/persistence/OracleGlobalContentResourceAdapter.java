package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ContentResource;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.Dependency;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ReviewFilter;
import com.nexoskill.evaluation.globalcontent.application.port.out.GlobalContentResourcePort;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class OracleGlobalContentResourceAdapter implements GlobalContentResourcePort {
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;
    private final GlobalContentPromotionRepository promotions;
    private final OrganizationGlobalContentGrantRepository grants;

    public OracleGlobalContentResourceAdapter(
            NamedParameterJdbcTemplate jdbc,
            JdbcTemplate plainJdbc,
            GlobalContentPromotionRepository promotions,
            OrganizationGlobalContentGrantRepository grants) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
        this.promotions = promotions;
        this.grants = grants;
    }

    @Override
    public List<ContentResource> review(ReviewFilter filter) {
        List<GlobalContentType> types = filter.contentType() == null
                ? List.of(GlobalContentType.CATEGORY, GlobalContentType.QUESTION,
                        GlobalContentType.FORM, GlobalContentType.COLLECTION)
                : List.of(filter.contentType());
        List<ContentResource> result = new ArrayList<>();
        for (GlobalContentType type : types) {
            result.addAll(loadAll(type));
        }
        String query = normalize(filter.query());
        return result.stream()
                .filter(resource -> query == null || searchable(resource).contains(query))
                .filter(resource -> filter.organizationPublicId() == null || filter.organizationPublicId().isBlank()
                        || Objects.equals(resource.ownerOrganizationPublicId(), filter.organizationPublicId()))
                .filter(resource -> filter.scope() == null || resource.scope() == filter.scope())
                .filter(resource -> filter.status() == null || filter.status().isBlank()
                        || "ALL".equalsIgnoreCase(filter.status())
                        || resource.status().equalsIgnoreCase(filter.status()))
                .filter(resource -> filter.creatorUserId() == null
                        || Objects.equals(resource.createdBy(), filter.creatorUserId()))
                .filter(resource -> filter.createdFrom() == null
                        || !resource.createdAt().isBefore(filter.createdFrom()))
                .filter(resource -> filter.createdTo() == null
                        || !resource.createdAt().isAfter(filter.createdTo()))
                .filter(resource -> filter.promoted() == null || resource.promoted() == filter.promoted())
                .filter(resource -> filter.distributed() == null || resource.distributed() == filter.distributed())
                .sorted(Comparator.comparing(ContentResource::createdAt).reversed())
                .toList();
    }

    @Override
    public ContentResource find(GlobalContentType type, String publicId) {
        if (type == GlobalContentType.PATH) unsupported(type);
        List<ContentResource> values = jdbc.query(baseSelect(type) + " WHERE c.PUBLIC_ID = :publicId",
                Map.of("publicId", canonicalUuid(publicId)), mapper(type));
        if (values.isEmpty()) {
            throw new BusinessException("GLOBAL_CONTENT_NOT_FOUND", "El contenido solicitado no existe.");
        }
        return enrich(values.getFirst());
    }

    @Override
    public ContentResource findByInternalId(GlobalContentType type, Long internalId) {
        if (type == GlobalContentType.PATH) unsupported(type);
        List<ContentResource> values = jdbc.query(baseSelect(type) + " WHERE c." + idColumn(type) + " = :id",
                Map.of("id", internalId), mapper(type));
        if (values.isEmpty()) {
            throw new BusinessException("GLOBAL_CONTENT_NOT_FOUND", "El contenido solicitado no existe.");
        }
        return enrich(values.getFirst());
    }

    @Override
    public List<Dependency> dependencies(GlobalContentType type, Long internalId) {
        return switch (type) {
            case CATEGORY -> List.of();
            case QUESTION -> questionDependencies(internalId);
            case FORM -> formDependencies(internalId);
            case COLLECTION -> collectionDependencies(internalId);
            case PATH -> throw unsupported(type);
        };
    }

    @Override
    public List<ContentResource> possibleGlobalDuplicates(ContentResource source) {
        return loadAll(source.contentType()).stream()
                .filter(candidate -> candidate.scope() == ContentScope.GLOBAL)
                .filter(candidate -> Objects.equals(candidate.functionalHash(), source.functionalHash())
                        || normalize(candidate.name()).equals(normalize(source.name())))
                .toList();
    }

    @Override
    public ContentResource copyToGlobal(GlobalContentType type, Long sourceInternalId,
            Map<ResourceKey, Long> dependencyTargets, Long actorUserId) {
        Long globalOrganizationId = globalOrganizationId();
        Long targetId = switch (type) {
            case CATEGORY -> copyCategory(sourceInternalId, globalOrganizationId, null, null, actorUserId, true);
            case QUESTION -> copyQuestion(sourceInternalId, globalOrganizationId, null, null,
                    dependencyTargets, actorUserId, true);
            case FORM -> copyForm(sourceInternalId, globalOrganizationId, null, null,
                    dependencyTargets, actorUserId, true);
            case COLLECTION -> copyLearningCollection(sourceInternalId, globalOrganizationId, null, null,
                    dependencyTargets, actorUserId, true);
            case PATH -> throw unsupported(type);
        };
        return findByInternalId(type, targetId);
    }

    @Override
    public ContentResource copyToOrganization(GlobalContentType type, Long globalInternalId, Long organizationId,
            long sourceGlobalVersion, Map<ResourceKey, Long> dependencyTargets, Long actorUserId, boolean editable) {
        Long targetId = switch (type) {
            case CATEGORY -> copyCategory(globalInternalId, organizationId, globalInternalId, sourceGlobalVersion,
                    actorUserId, false);
            case QUESTION -> copyQuestion(globalInternalId, organizationId, globalInternalId, sourceGlobalVersion,
                    dependencyTargets, actorUserId, false);
            case FORM -> copyForm(globalInternalId, organizationId, globalInternalId, sourceGlobalVersion,
                    dependencyTargets, actorUserId, false);
            case COLLECTION -> copyLearningCollection(globalInternalId, organizationId, globalInternalId,
                    sourceGlobalVersion, dependencyTargets, actorUserId, false);
            case PATH -> throw unsupported(type);
        };
        return findByInternalId(type, targetId);
    }

    @Override
    public void markCustomized(GlobalContentType type, Long targetInternalId) {
        if (type == GlobalContentType.PATH) return;
        jdbc.update("UPDATE " + table(type)
                        + " SET IS_CUSTOMIZED = 1, SYNC_STATUS = 'DIVERGED' WHERE " + idColumn(type) + " = :id"
                        + " AND SOURCE_GLOBAL_ID IS NOT NULL",
                Map.of("id", targetInternalId));
    }

    @Override
    public void changeOperationalStatus(GlobalContentType type, Long internalId, String status, Long actorUserId) {
        if (type == GlobalContentType.PATH) throw unsupported(type);
        String updatedBy = type == GlobalContentType.QUESTION || type == GlobalContentType.CATEGORY
                || type == GlobalContentType.FORM || type == GlobalContentType.COLLECTION
                ? ", UPDATED_BY = :actor, UPDATED_AT = SYSTIMESTAMP" : "";
        jdbc.update("UPDATE " + table(type) + " SET STATUS = :status" + updatedBy
                        + " WHERE " + idColumn(type) + " = :id AND CONTENT_SCOPE = 'GLOBAL'",
                new MapSqlParameterSource().addValue("status", status).addValue("actor", actorUserId)
                        .addValue("id", internalId));
    }

    private List<ContentResource> loadAll(GlobalContentType type) {
        if (type == GlobalContentType.PATH) return List.of();
        return jdbc.query(baseSelect(type), Map.of(), mapper(type)).stream().map(this::enrich).toList();
    }

    private ContentResource enrich(ContentResource resource) {
        boolean promoted = promotions.findByContentTypeAndSourceContentIdAndSourceVersion(
                resource.contentType(), resource.internalId(), resource.version()).isPresent();
        boolean distributed;
        if (resource.scope() == ContentScope.GLOBAL) {
            distributed = !grants.findAllByContentTypeAndGlobalContentIdAndStatus(
                    resource.contentType(), resource.internalId(),
                    com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus.ACTIVE).isEmpty();
        } else {
            distributed = promotions.findFirstByContentTypeAndSourceContentIdAndStatusOrderByPromotedAtDesc(
                    resource.contentType(), resource.internalId(),
                    com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus.PUBLISHED)
                    .map(promotion -> !grants.findAllByContentTypeAndGlobalContentIdAndStatus(
                            resource.contentType(), promotion.getGlobalContentId(),
                            com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus.ACTIVE).isEmpty())
                    .orElse(false);
        }
        return new ContentResource(resource.contentType(), resource.internalId(), resource.publicId(), resource.name(),
                resource.description(), resource.status(), resource.scope(), resource.ownerOrganizationId(),
                resource.ownerOrganizationPublicId(), resource.ownerOrganizationName(), resource.createdBy(),
                resource.createdAt(), resource.updatedBy(), resource.updatedAt(), resource.version(), promoted,
                distributed, resource.functionalHash());
    }

    private RowMapper<ContentResource> mapper(GlobalContentType type) {
        return (rs, rowNum) -> {
            Long id = rs.getLong("CONTENT_ID");
            String publicId = rs.getString("PUBLIC_ID");
            String name = rs.getString("CONTENT_NAME");
            String description = rs.getString("CONTENT_DESCRIPTION");
            String status = rs.getString("STATUS");
            ContentScope scope = ContentScope.valueOf(rs.getString("CONTENT_SCOPE"));
            Long ownerId = nullableLong(rs, "OWNER_ORGANIZATION_ID");
            Long createdBy = nullableLong(rs, "CREATED_BY");
            Long updatedBy = nullableLong(rs, "UPDATED_BY");
            Instant createdAt = instant(rs.getObject("CREATED_AT"));
            Instant updatedAt = instant(rs.getObject("UPDATED_AT"));
            long version = rs.getLong("VERSION_NO");
            String hash = functionalHash(type, id);
            return new ContentResource(type, id, publicId, name, description, status, scope, ownerId,
                    rs.getString("OWNER_ORGANIZATION_PUBLIC_ID"), rs.getString("OWNER_ORGANIZATION_NAME"),
                    createdBy, createdAt, updatedBy, updatedAt, version, false, false, hash);
        };
    }

    private String baseSelect(GlobalContentType type) {
        return switch (type) {
            case CATEGORY -> """
                    SELECT c.CATEGORY_ID CONTENT_ID, c.PUBLIC_ID, c.CATEGORY_NAME CONTENT_NAME,
                           c.DESCRIPTION CONTENT_DESCRIPTION, c.STATUS, c.CONTENT_SCOPE,
                           c.OWNER_ORGANIZATION_ID, o.PUBLIC_ID OWNER_ORGANIZATION_PUBLIC_ID,
                           o.ORGANIZATION_NAME OWNER_ORGANIZATION_NAME, c.CREATED_BY, c.CREATED_AT,
                           c.UPDATED_BY, NVL(c.UPDATED_AT, c.CREATED_AT) UPDATED_AT, c.VERSION_NO
                      FROM QUESTION_CATEGORY c
                      LEFT JOIN ORGANIZATION o ON o.ORGANIZATION_ID = c.OWNER_ORGANIZATION_ID
                    """;
            case QUESTION -> """
                    SELECT c.QUESTION_ID CONTENT_ID, c.PUBLIC_ID,
                           DBMS_LOB.SUBSTR(c.STATEMENT_TEXT, 1000, 1) CONTENT_NAME,
                           DBMS_LOB.SUBSTR(c.EXPLANATION_TEXT, 2000, 1) CONTENT_DESCRIPTION,
                           c.STATUS, c.CONTENT_SCOPE, c.OWNER_ORGANIZATION_ID,
                           o.PUBLIC_ID OWNER_ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_NAME OWNER_ORGANIZATION_NAME,
                           c.CREATED_BY, c.CREATED_AT, c.UPDATED_BY, NVL(c.UPDATED_AT, c.CREATED_AT) UPDATED_AT,
                           c.VERSION_NO
                      FROM QUESTION c
                      LEFT JOIN ORGANIZATION o ON o.ORGANIZATION_ID = c.OWNER_ORGANIZATION_ID
                    """;
            case FORM -> """
                    SELECT c.FORM_ID CONTENT_ID, c.PUBLIC_ID, c.TITLE CONTENT_NAME,
                           c.DESCRIPTION CONTENT_DESCRIPTION, c.STATUS, c.CONTENT_SCOPE,
                           c.OWNER_ORGANIZATION_ID, o.PUBLIC_ID OWNER_ORGANIZATION_PUBLIC_ID,
                           o.ORGANIZATION_NAME OWNER_ORGANIZATION_NAME, c.CREATED_BY, c.CREATED_AT,
                           c.UPDATED_BY, NVL(c.UPDATED_AT, c.CREATED_AT) UPDATED_AT, c.VERSION_NO
                      FROM EVALUATION_FORM c
                      LEFT JOIN ORGANIZATION o ON o.ORGANIZATION_ID = c.OWNER_ORGANIZATION_ID
                    """;
            case COLLECTION -> """
                    SELECT c.COLLECTION_ID CONTENT_ID, c.PUBLIC_ID, c.COLLECTION_NAME CONTENT_NAME,
                           c.DESCRIPTION CONTENT_DESCRIPTION, c.STATUS, c.CONTENT_SCOPE,
                           c.OWNER_ORGANIZATION_ID, o.PUBLIC_ID OWNER_ORGANIZATION_PUBLIC_ID,
                           o.ORGANIZATION_NAME OWNER_ORGANIZATION_NAME, c.CREATED_BY, c.CREATED_AT,
                           c.UPDATED_BY, NVL(c.UPDATED_AT, c.CREATED_AT) UPDATED_AT, c.VERSION_NO
                      FROM LEARNING_COLLECTION c
                      LEFT JOIN ORGANIZATION o ON o.ORGANIZATION_ID = c.OWNER_ORGANIZATION_ID
                    """;
            case PATH -> throw unsupported(type);
        };
    }

    private List<Dependency> questionDependencies(Long questionId) {
        String sql = """
                SELECT c.CATEGORY_ID CONTENT_ID, c.PUBLIC_ID, c.CATEGORY_NAME CONTENT_NAME,
                       c.CONTENT_SCOPE, c.OWNER_ORGANIZATION_ID, c.VERSION_NO
                  FROM QUESTION_CATEGORY_RELATION relation
                  JOIN QUESTION_CATEGORY c ON c.CATEGORY_ID = relation.CATEGORY_ID
                 WHERE relation.QUESTION_ID = :id
                 ORDER BY c.CATEGORY_NAME
                """;
        return jdbc.query(sql, Map.of("id", questionId), (rs, rowNum) -> dependency(
                GlobalContentType.CATEGORY, rs.getLong("CONTENT_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("CONTENT_NAME"), ContentScope.valueOf(rs.getString("CONTENT_SCOPE")),
                nullableLong(rs, "OWNER_ORGANIZATION_ID"), rs.getLong("VERSION_NO")));
    }

    private List<Dependency> formDependencies(Long formId) {
        List<Dependency> result = new ArrayList<>();
        String questionSql = """
                SELECT DISTINCT q.QUESTION_ID CONTENT_ID, q.PUBLIC_ID,
                       DBMS_LOB.SUBSTR(q.STATEMENT_TEXT, 500, 1) CONTENT_NAME,
                       q.CONTENT_SCOPE, q.OWNER_ORGANIZATION_ID, q.VERSION_NO
                  FROM FORM_SECTION s
                  JOIN FORM_QUESTION fq ON fq.SECTION_ID = s.SECTION_ID
                  JOIN QUESTION q ON q.QUESTION_ID = fq.QUESTION_ID
                 WHERE s.FORM_ID = :id
                """;
        result.addAll(jdbc.query(questionSql, Map.of("id", formId), (rs, rowNum) -> dependency(
                GlobalContentType.QUESTION, rs.getLong("CONTENT_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("CONTENT_NAME"), ContentScope.valueOf(rs.getString("CONTENT_SCOPE")),
                nullableLong(rs, "OWNER_ORGANIZATION_ID"), rs.getLong("VERSION_NO"))));
        String categorySql = """
                SELECT DISTINCT c.CATEGORY_ID CONTENT_ID, c.PUBLIC_ID, c.CATEGORY_NAME CONTENT_NAME,
                       c.CONTENT_SCOPE, c.OWNER_ORGANIZATION_ID, c.VERSION_NO
                  FROM FORM_SECTION s
                  JOIN FORM_QUESTION_POOL fp ON fp.SECTION_ID = s.SECTION_ID AND fp.SOURCE_TYPE = 'CATEGORY'
                  JOIN QUESTION_CATEGORY c ON c.CATEGORY_ID = fp.CATEGORY_ID
                 WHERE s.FORM_ID = :id
                """;
        result.addAll(jdbc.query(categorySql, Map.of("id", formId), (rs, rowNum) -> dependency(
                GlobalContentType.CATEGORY, rs.getLong("CONTENT_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("CONTENT_NAME"), ContentScope.valueOf(rs.getString("CONTENT_SCOPE")),
                nullableLong(rs, "OWNER_ORGANIZATION_ID"), rs.getLong("VERSION_NO"))));
        return result.stream().distinct().toList();
    }

    private List<Dependency> collectionDependencies(Long collectionId) {
        String sql = """
                SELECT DISTINCT f.FORM_ID CONTENT_ID, f.PUBLIC_ID, f.TITLE CONTENT_NAME,
                       f.CONTENT_SCOPE, f.OWNER_ORGANIZATION_ID, f.VERSION_NO
                  FROM LEARNING_COLLECTION_LEVEL level_item
                  JOIN EVALUATION_FORM f ON f.FORM_ID = level_item.FORM_ID
                 WHERE level_item.COLLECTION_ID = :id
                 ORDER BY f.TITLE
                """;
        return jdbc.query(sql, Map.of("id", collectionId), (rs, rowNum) -> dependency(
                GlobalContentType.FORM, rs.getLong("CONTENT_ID"), rs.getString("PUBLIC_ID"),
                rs.getString("CONTENT_NAME"), ContentScope.valueOf(rs.getString("CONTENT_SCOPE")),
                nullableLong(rs, "OWNER_ORGANIZATION_ID"), rs.getLong("VERSION_NO")));
    }

    private Dependency dependency(GlobalContentType type, Long id, String publicId, String name,
            ContentScope scope, Long ownerOrganizationId, long version) {
        Optional<GlobalContentPromotionJpaEntity> promotion = scope == ContentScope.GLOBAL
                ? Optional.empty()
                : promotions.findByContentTypeAndSourceContentIdAndSourceVersion(type, id, version);
        return new Dependency(type, id, publicId, name, scope, ownerOrganizationId, version,
                scope == ContentScope.GLOBAL || promotion.isPresent(),
                scope == ContentScope.GLOBAL ? publicId : promotion.map(GlobalContentPromotionJpaEntity::getGlobalContentPublicId).orElse(null));
    }

    private Long copyCategory(Long sourceId, Long ownerOrganizationId, Long sourceGlobalId,
            Long sourceGlobalVersion, Long actor, boolean global) {
        Map<String, Object> source = requiredRow("SELECT * FROM QUESTION_CATEGORY WHERE CATEGORY_ID = :id", sourceId);
        String publicId = UUID.randomUUID().toString();
        String code = uniqueCode(GlobalContentType.CATEGORY, Objects.toString(source.get("CATEGORY_CODE")),
                ownerOrganizationId, global);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("publicId", publicId)
                .addValue("code", code)
                .addValue("name", source.get("CATEGORY_NAME"))
                .addValue("description", source.get("DESCRIPTION"))
                .addValue("scope", global ? "GLOBAL" : "ORGANIZATION")
                .addValue("owner", ownerOrganizationId)
                .addValue("sourceGlobalId", sourceGlobalId)
                .addValue("sourceGlobalVersion", sourceGlobalVersion)
                .addValue("actor", actor);
        return insert("""
                INSERT INTO QUESTION_CATEGORY (
                    PUBLIC_ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, STATUS, CONTENT_SCOPE,
                    OWNER_ORGANIZATION_ID, SOURCE_GLOBAL_ID, SOURCE_GLOBAL_VERSION, IS_CUSTOMIZED,
                    LAST_SYNCHRONIZED_AT, SYNC_STATUS, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT, VERSION_NO
                ) VALUES (
                    :publicId, :code, :name, :description, 'ACTIVE', :scope,
                    :owner, :sourceGlobalId, :sourceGlobalVersion, 0,
                    CASE WHEN :sourceGlobalId IS NULL THEN NULL ELSE SYSTIMESTAMP END,
                    CASE WHEN :sourceGlobalId IS NULL THEN 'NOT_LINKED' ELSE 'SYNCHRONIZED' END,
                    :actor, SYSTIMESTAMP, :actor, SYSTIMESTAMP, 0
                )
                """, parameters, "CATEGORY_ID");
    }

    private Long copyQuestion(Long sourceId, Long ownerOrganizationId, Long sourceGlobalId,
            Long sourceGlobalVersion, Map<ResourceKey, Long> dependencyTargets, Long actor, boolean global) {
        Map<String, Object> source = requiredRow("SELECT * FROM QUESTION WHERE QUESTION_ID = :id", sourceId);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("publicId", UUID.randomUUID().toString())
                .addValue("typeCode", source.get("TYPE_CODE"))
                .addValue("difficultyCode", source.get("DIFFICULTY_CODE"))
                .addValue("status", "ACTIVE")
                .addValue("statement", source.get("STATEMENT_TEXT"))
                .addValue("explanation", source.get("EXPLANATION_TEXT"))
                .addValue("promptMedia", source.get("PROMPT_MEDIA_ID"))
                .addValue("codeLanguage", source.get("CODE_LANGUAGE"))
                .addValue("codeContent", source.get("CODE_CONTENT"))
                .addValue("answers", source.get("ACCEPTED_ANSWERS_JSON"))
                .addValue("caseSensitive", source.get("ANSWER_CASE_SENSITIVE"))
                .addValue("manualReview", source.get("MANUAL_REVIEW"))
                .addValue("numericMin", source.get("NUMERIC_MIN"))
                .addValue("numericMax", source.get("NUMERIC_MAX"))
                .addValue("numericTolerance", source.get("NUMERIC_TOLERANCE"))
                .addValue("maxLength", source.get("RESPONSE_MAX_LENGTH"))
                .addValue("scope", global ? "GLOBAL" : "ORGANIZATION")
                .addValue("owner", ownerOrganizationId)
                .addValue("sourceGlobalId", sourceGlobalId)
                .addValue("sourceGlobalVersion", sourceGlobalVersion)
                .addValue("actor", actor);
        Long targetId = insert("""
                INSERT INTO QUESTION (
                    PUBLIC_ID, TYPE_CODE, DIFFICULTY_CODE, STATUS, STATEMENT_TEXT, EXPLANATION_TEXT,
                    PROMPT_MEDIA_ID, CODE_LANGUAGE, CODE_CONTENT, ACCEPTED_ANSWERS_JSON,
                    ANSWER_CASE_SENSITIVE, MANUAL_REVIEW, NUMERIC_MIN, NUMERIC_MAX, NUMERIC_TOLERANCE,
                    RESPONSE_MAX_LENGTH, CONTENT_SCOPE, OWNER_ORGANIZATION_ID, SOURCE_GLOBAL_ID,
                    SOURCE_GLOBAL_VERSION, IS_CUSTOMIZED, LAST_SYNCHRONIZED_AT, SYNC_STATUS,
                    CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT, VERSION_NO
                ) VALUES (
                    :publicId, :typeCode, :difficultyCode, :status, :statement, :explanation,
                    :promptMedia, :codeLanguage, :codeContent, :answers,
                    :caseSensitive, :manualReview, :numericMin, :numericMax, :numericTolerance,
                    :maxLength, :scope, :owner, :sourceGlobalId,
                    :sourceGlobalVersion, 0,
                    CASE WHEN :sourceGlobalId IS NULL THEN NULL ELSE SYSTIMESTAMP END,
                    CASE WHEN :sourceGlobalId IS NULL THEN 'NOT_LINKED' ELSE 'SYNCHRONIZED' END,
                    :actor, SYSTIMESTAMP, :actor, SYSTIMESTAMP, 0
                )
                """, parameters, "QUESTION_ID");
        List<Long> categoryIds = jdbc.queryForList(
                "SELECT CATEGORY_ID FROM QUESTION_CATEGORY_RELATION WHERE QUESTION_ID = :id ORDER BY CATEGORY_ID",
                Map.of("id", sourceId), Long.class);
        for (Long categoryId : categoryIds) {
            Long targetCategoryId = dependencyTargets.getOrDefault(
                    new ResourceKey(GlobalContentType.CATEGORY, categoryId), categoryId);
            jdbc.update("INSERT INTO QUESTION_CATEGORY_RELATION (QUESTION_ID, CATEGORY_ID) VALUES (:questionId, :categoryId)",
                    Map.of("questionId", targetId, "categoryId", targetCategoryId));
        }
        List<Map<String, Object>> options = jdbc.queryForList(
                "SELECT * FROM QUESTION_OPTION WHERE QUESTION_ID = :id ORDER BY OPTION_ORDER", Map.of("id", sourceId));
        for (Map<String, Object> option : options) {
            MapSqlParameterSource optionParams = new MapSqlParameterSource()
                    .addValue("publicId", UUID.randomUUID().toString())
                    .addValue("questionId", targetId)
                    .addValue("order", option.get("OPTION_ORDER"))
                    .addValue("text", option.get("OPTION_TEXT"))
                    .addValue("media", option.get("MEDIA_ID"))
                    .addValue("matchText", option.get("MATCH_TEXT"))
                    .addValue("matchMedia", option.get("MATCH_MEDIA_ID"))
                    .addValue("correct", option.get("IS_CORRECT"))
                    .addValue("feedback", option.get("FEEDBACK_TEXT"));
            jdbc.update("""
                    INSERT INTO QUESTION_OPTION (
                        PUBLIC_ID, QUESTION_ID, OPTION_ORDER, OPTION_TEXT, MEDIA_ID, MATCH_TEXT,
                        MATCH_MEDIA_ID, IS_CORRECT, FEEDBACK_TEXT, CREATED_AT
                    ) VALUES (
                        :publicId, :questionId, :order, :text, :media, :matchText,
                        :matchMedia, :correct, :feedback, SYSTIMESTAMP
                    )
                    """, optionParams);
        }
        return targetId;
    }

    private Long copyForm(Long sourceId, Long ownerOrganizationId, Long sourceGlobalId,
            Long sourceGlobalVersion, Map<ResourceKey, Long> dependencyTargets, Long actor, boolean global) {
        Map<String, Object> source = requiredRow("SELECT * FROM EVALUATION_FORM WHERE FORM_ID = :id", sourceId);
        String code = uniqueCode(GlobalContentType.FORM, Objects.toString(source.get("FORM_CODE")),
                ownerOrganizationId, global);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("publicId", UUID.randomUUID().toString()).addValue("code", code)
                .addValue("title", source.get("TITLE")).addValue("description", source.get("DESCRIPTION"))
                .addValue("mode", source.get("MODE_CODE")).addValue("passing", source.get("PASSING_SCORE"))
                .addValue("maxAttempts", source.get("MAX_ATTEMPTS")).addValue("retry", source.get("RETRY_UNTIL_PASSED"))
                .addValue("accept", source.get("ACCEPT_RESPONSES")).addValue("starts", source.get("STARTS_AT"))
                .addValue("ends", source.get("ENDS_AT")).addValue("duration", source.get("DURATION_MINUTES"))
                .addValue("showResults", source.get("SHOW_RESULTS")).addValue("showCorrect", source.get("SHOW_CORRECT_ANSWERS"))
                .addValue("randomQuestions", source.get("RANDOMIZE_QUESTIONS")).addValue("randomOptions", source.get("RANDOMIZE_OPTIONS"))
                .addValue("showProgress", source.get("SHOW_PROGRESS")).addValue("hideNumbers", source.get("HIDE_QUESTION_NUMBERS"))
                .addValue("saveResume", source.get("ALLOW_SAVE_RESUME")).addValue("oneAttempt", source.get("ONE_ACTIVE_ATTEMPT"))
                .addValue("message", source.get("THANK_YOU_MESSAGE"))
                .addValue("scope", global ? "GLOBAL" : "ORGANIZATION").addValue("owner", ownerOrganizationId)
                .addValue("sourceGlobalId", sourceGlobalId).addValue("sourceGlobalVersion", sourceGlobalVersion)
                .addValue("actor", actor);
        Long targetId = insert("""
                INSERT INTO EVALUATION_FORM (
                    PUBLIC_ID, FORM_CODE, TITLE, DESCRIPTION, STATUS, MODE_CODE, PASSING_SCORE, MAX_ATTEMPTS,
                    RETRY_UNTIL_PASSED, ACCEPT_RESPONSES, STARTS_AT, ENDS_AT, DURATION_MINUTES, SHOW_RESULTS,
                    SHOW_CORRECT_ANSWERS, RANDOMIZE_QUESTIONS, RANDOMIZE_OPTIONS, SHOW_PROGRESS,
                    HIDE_QUESTION_NUMBERS, ALLOW_SAVE_RESUME, ONE_ACTIVE_ATTEMPT, THANK_YOU_MESSAGE,
                    CONTENT_SCOPE, OWNER_ORGANIZATION_ID, SOURCE_GLOBAL_ID, SOURCE_GLOBAL_VERSION,
                    IS_CUSTOMIZED, LAST_SYNCHRONIZED_AT, SYNC_STATUS, CREATED_BY, CREATED_AT, UPDATED_BY,
                    UPDATED_AT, VERSION_NO
                ) VALUES (
                    :publicId, :code, :title, :description, 'DRAFT', :mode, :passing, :maxAttempts,
                    :retry, :accept, :starts, :ends, :duration, :showResults,
                    :showCorrect, :randomQuestions, :randomOptions, :showProgress,
                    :hideNumbers, :saveResume, :oneAttempt, :message,
                    :scope, :owner, :sourceGlobalId, :sourceGlobalVersion,
                    0, CASE WHEN :sourceGlobalId IS NULL THEN NULL ELSE SYSTIMESTAMP END,
                    CASE WHEN :sourceGlobalId IS NULL THEN 'NOT_LINKED' ELSE 'SYNCHRONIZED' END,
                    :actor, SYSTIMESTAMP, :actor, SYSTIMESTAMP, 0
                )
                """, parameters, "FORM_ID");
        List<Map<String, Object>> sections = jdbc.queryForList(
                "SELECT * FROM FORM_SECTION WHERE FORM_ID = :id ORDER BY SECTION_ORDER", Map.of("id", sourceId));
        for (Map<String, Object> section : sections) {
            Long sourceSectionId = ((Number) section.get("SECTION_ID")).longValue();
            MapSqlParameterSource sectionParams = new MapSqlParameterSource()
                    .addValue("formId", targetId).addValue("publicId", UUID.randomUUID().toString())
                    .addValue("title", section.get("TITLE")).addValue("description", section.get("DESCRIPTION"))
                    .addValue("order", section.get("SECTION_ORDER"));
            Long targetSectionId = insert("""
                    INSERT INTO FORM_SECTION (FORM_ID, PUBLIC_ID, TITLE, DESCRIPTION, SECTION_ORDER)
                    VALUES (:formId, :publicId, :title, :description, :order)
                    """, sectionParams, "SECTION_ID");
            List<Map<String, Object>> fixed = jdbc.queryForList(
                    "SELECT * FROM FORM_QUESTION WHERE SECTION_ID = :id ORDER BY QUESTION_ORDER",
                    Map.of("id", sourceSectionId));
            for (Map<String, Object> item : fixed) {
                Long sourceQuestionId = ((Number) item.get("QUESTION_ID")).longValue();
                Long targetQuestionId = dependencyTargets.getOrDefault(
                        new ResourceKey(GlobalContentType.QUESTION, sourceQuestionId), sourceQuestionId);
                jdbc.update("""
                        INSERT INTO FORM_QUESTION (SECTION_ID, QUESTION_ID, QUESTION_ORDER, POINTS, REQUIRED)
                        VALUES (:section, :question, :position, :points, :required)
                        """, new MapSqlParameterSource().addValue("section", targetSectionId)
                        .addValue("question", targetQuestionId).addValue("position", item.get("QUESTION_ORDER"))
                        .addValue("points", item.get("POINTS")).addValue("required", item.get("REQUIRED")));
            }
            List<Map<String, Object>> pools = jdbc.queryForList(
                    "SELECT * FROM FORM_QUESTION_POOL WHERE SECTION_ID = :id ORDER BY POOL_ORDER",
                    Map.of("id", sourceSectionId));
            for (Map<String, Object> pool : pools) {
                Long categoryId = pool.get("CATEGORY_ID") == null ? null : ((Number) pool.get("CATEGORY_ID")).longValue();
                if (categoryId != null) {
                    categoryId = dependencyTargets.getOrDefault(new ResourceKey(GlobalContentType.CATEGORY, categoryId), categoryId);
                }
                jdbc.update("""
                        INSERT INTO FORM_QUESTION_POOL (
                            SECTION_ID, PUBLIC_ID, SOURCE_TYPE, COLLECTION_ID, CATEGORY_ID,
                            QUESTION_COUNT, DIFFICULTY_CODE, POOL_ORDER
                        ) VALUES (
                            :section, :publicId, :sourceType, :collectionId, :categoryId,
                            :count, :difficulty, :position
                        )
                        """, new MapSqlParameterSource().addValue("section", targetSectionId)
                        .addValue("publicId", UUID.randomUUID().toString())
                        .addValue("sourceType", pool.get("SOURCE_TYPE"))
                        .addValue("collectionId", pool.get("COLLECTION_ID"))
                        .addValue("categoryId", categoryId).addValue("count", pool.get("QUESTION_COUNT"))
                        .addValue("difficulty", pool.get("DIFFICULTY_CODE"))
                        .addValue("position", pool.get("POOL_ORDER")));
            }
        }
        return targetId;
    }

    private Long copyLearningCollection(Long sourceId, Long ownerOrganizationId, Long sourceGlobalId,
            Long sourceGlobalVersion, Map<ResourceKey, Long> dependencyTargets, Long actor, boolean global) {
        Map<String, Object> source = requiredRow("SELECT * FROM LEARNING_COLLECTION WHERE COLLECTION_ID = :id", sourceId);
        String code = uniqueCode(GlobalContentType.COLLECTION, Objects.toString(source.get("COLLECTION_CODE")),
                ownerOrganizationId, global);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("publicId", UUID.randomUUID().toString()).addValue("code", code)
                .addValue("name", source.get("COLLECTION_NAME")).addValue("description", source.get("DESCRIPTION"))
                .addValue("scope", global ? "GLOBAL" : "ORGANIZATION").addValue("owner", ownerOrganizationId)
                .addValue("sourceGlobalId", sourceGlobalId).addValue("sourceGlobalVersion", sourceGlobalVersion)
                .addValue("actor", actor);
        Long targetId = insert("""
                INSERT INTO LEARNING_COLLECTION (
                    PUBLIC_ID, COLLECTION_CODE, COLLECTION_NAME, DESCRIPTION, STATUS,
                    CONTENT_SCOPE, OWNER_ORGANIZATION_ID, SOURCE_GLOBAL_ID, SOURCE_GLOBAL_VERSION,
                    IS_CUSTOMIZED, LAST_SYNCHRONIZED_AT, SYNC_STATUS, CREATED_BY, CREATED_AT,
                    UPDATED_BY, UPDATED_AT, VERSION_NO
                ) VALUES (
                    :publicId, :code, :name, :description, 'DRAFT',
                    :scope, :owner, :sourceGlobalId, :sourceGlobalVersion,
                    0, CASE WHEN :sourceGlobalId IS NULL THEN NULL ELSE SYSTIMESTAMP END,
                    CASE WHEN :sourceGlobalId IS NULL THEN 'NOT_LINKED' ELSE 'SYNCHRONIZED' END,
                    :actor, SYSTIMESTAMP, :actor, SYSTIMESTAMP, 0
                )
                """, parameters, "COLLECTION_ID");
        List<Map<String, Object>> levels = jdbc.queryForList(
                "SELECT * FROM LEARNING_COLLECTION_LEVEL WHERE COLLECTION_ID = :id ORDER BY LEVEL_ORDER",
                Map.of("id", sourceId));
        for (Map<String, Object> level : levels) {
            Long sourceFormId = ((Number) level.get("FORM_ID")).longValue();
            Long targetFormId = dependencyTargets.getOrDefault(
                    new ResourceKey(GlobalContentType.FORM, sourceFormId), sourceFormId);
            jdbc.update("""
                    INSERT INTO LEARNING_COLLECTION_LEVEL (
                        COLLECTION_ID, FORM_ID, LEVEL_ORDER, UNLOCK_RULE, CREATED_AT
                    ) VALUES (:collection, :form, :position, :rule, SYSTIMESTAMP)
                    """, new MapSqlParameterSource().addValue("collection", targetId).addValue("form", targetFormId)
                    .addValue("position", level.get("LEVEL_ORDER")).addValue("rule", level.get("UNLOCK_RULE")));
        }
        return targetId;
    }

    private String functionalHash(GlobalContentType type, Long internalId) {
        String data = switch (type) {
            case CATEGORY -> scalar("""
                    SELECT UPPER(CATEGORY_NAME) || '|' || NVL(UPPER(DESCRIPTION), '')
                      FROM QUESTION_CATEGORY WHERE CATEGORY_ID = :id
                    """, internalId);
            case QUESTION -> scalar("""
                    SELECT TYPE_CODE || '|' || DBMS_LOB.SUBSTR(STATEMENT_TEXT, 4000, 1) || '|'
                           || NVL(DBMS_LOB.SUBSTR(EXPLANATION_TEXT, 4000, 1), '') || '|'
                           || NVL(DBMS_LOB.SUBSTR(CODE_CONTENT, 4000, 1), '')
                      FROM QUESTION WHERE QUESTION_ID = :id
                    """, internalId) + "|" + dependencySignature(GlobalContentType.QUESTION, internalId);
            case FORM -> scalar("""
                    SELECT UPPER(TITLE) || '|' || MODE_CODE || '|' || PASSING_SCORE || '|'
                           || NVL(DURATION_MINUTES, 0) || '|' || NVL(MAX_ATTEMPTS, 0)
                      FROM EVALUATION_FORM WHERE FORM_ID = :id
                    """, internalId) + "|" + dependencySignature(GlobalContentType.FORM, internalId);
            case COLLECTION -> scalar("""
                    SELECT UPPER(COLLECTION_NAME) || '|' || NVL(UPPER(DESCRIPTION), '')
                      FROM LEARNING_COLLECTION WHERE COLLECTION_ID = :id
                    """, internalId) + "|" + dependencySignature(GlobalContentType.COLLECTION, internalId);
            case PATH -> throw unsupported(type);
        };
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible calcular la huella funcional.", exception);
        }
    }

    private String dependencySignature(GlobalContentType type, Long id) {
        return dependencies(type, id).stream()
                .map(dependency -> dependency.contentType() + ":" + dependency.publicId() + ":" + dependency.version())
                .sorted().reduce((left, right) -> left + "|" + right).orElse("");
    }

    private String uniqueCode(GlobalContentType type, String sourceCode, Long ownerOrganizationId, boolean global) {
        String base = sourceCode == null || sourceCode.isBlank() ? type.name() : sourceCode.trim().toUpperCase(Locale.ROOT);
        String suffix = global ? "_GLOBAL" : "_COPY";
        String candidate = truncate(base + suffix, 72);
        int sequence = 2;
        while (codeExists(type, candidate, ownerOrganizationId, global)) {
            candidate = truncate(base + suffix + "_" + sequence++, 80);
        }
        return candidate;
    }

    private boolean codeExists(GlobalContentType type, String code, Long ownerOrganizationId, boolean global) {
        String sql = switch (type) {
            case CATEGORY -> "SELECT COUNT(*) FROM QUESTION_CATEGORY WHERE UPPER(CATEGORY_CODE)=UPPER(:code) AND CONTENT_SCOPE=:scope AND NVL(OWNER_ORGANIZATION_ID,-1)=NVL(:owner,-1)";
            case FORM -> "SELECT COUNT(*) FROM EVALUATION_FORM WHERE UPPER(FORM_CODE)=UPPER(:code) AND CONTENT_SCOPE=:scope AND NVL(OWNER_ORGANIZATION_ID,-1)=NVL(:owner,-1)";
            case COLLECTION -> "SELECT COUNT(*) FROM LEARNING_COLLECTION WHERE UPPER(COLLECTION_CODE)=UPPER(:code) AND CONTENT_SCOPE=:scope AND NVL(OWNER_ORGANIZATION_ID,-1)=NVL(:owner,-1)";
            default -> null;
        };
        if (sql == null) return false;
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource().addValue("code", code)
                .addValue("scope", global ? "GLOBAL" : "ORGANIZATION").addValue("owner", ownerOrganizationId), Integer.class);
        return count != null && count > 0;
    }


    private Long globalOrganizationId() {
        Long value = jdbc.queryForObject("""
                SELECT ORGANIZATION_ID
                  FROM ORGANIZATION
                 WHERE ORGANIZATION_TYPE = 'GLOBAL'
                """, Map.of(), Long.class);
        if (value == null) {
            throw new BusinessException("GLOBAL_ORGANIZATION_NOT_FOUND",
                    "La organización GLOBAL no se encuentra configurada.");
        }
        return value;
    }

    private Map<String, Object> requiredRow(String sql, Long id) {
        List<Map<String, Object>> values = jdbc.queryForList(sql, Map.of("id", id));
        if (values.isEmpty()) {
            throw new BusinessException("GLOBAL_CONTENT_NOT_FOUND", "El contenido solicitado no existe.");
        }
        return values.getFirst();
    }

    private Long insert(String sql, MapSqlParameterSource parameters, String keyColumn) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, parameters, keyHolder, new String[] {keyColumn});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("GLOBAL_CONTENT_COPY_FAILED", "No fue posible obtener el identificador de la copia creada.");
        }
        return key.longValue();
    }

    private String scalar(String sql, Long id) {
        String value = jdbc.queryForObject(sql, Map.of("id", id), String.class);
        return value == null ? "" : value;
    }

    private String searchable(ContentResource resource) {
        return normalize(resource.name() + " " + Objects.toString(resource.description(), "") + " "
                + Objects.toString(resource.ownerOrganizationName(), "") + " " + resource.contentType());
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return Instant.EPOCH;
    }

    private static String canonicalUuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("GLOBAL_CONTENT_ID_INVALID", "El identificador de contenido no es válido.");
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String table(GlobalContentType type) {
        return switch (type) {
            case CATEGORY -> "QUESTION_CATEGORY";
            case QUESTION -> "QUESTION";
            case FORM -> "EVALUATION_FORM";
            case COLLECTION -> "LEARNING_COLLECTION";
            case PATH -> throw unsupported(type);
        };
    }

    private static String idColumn(GlobalContentType type) {
        return switch (type) {
            case CATEGORY -> "CATEGORY_ID";
            case QUESTION -> "QUESTION_ID";
            case FORM -> "FORM_ID";
            case COLLECTION -> "COLLECTION_ID";
            case PATH -> throw unsupported(type);
        };
    }

    private static BusinessException unsupported(GlobalContentType type) {
        return new BusinessException("GLOBAL_CONTENT_TYPE_NOT_AVAILABLE",
                "El tipo de contenido " + type + " todavía no está implementado en el proyecto base.");
    }
}
