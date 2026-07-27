package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.application.model.QuestionTagView;
import com.nexoskill.evaluation.questionbank.application.service.QuestionTagNormalizer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionTagStore {
    private final NamedParameterJdbcTemplate jdbc;

    public QuestionTagStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replace(long questionId, List<String> rawTags, ContentScope scope,
            Long ownerOrganizationId, Long actorUserId, Instant occurredAt) {
        List<QuestionTagNormalizer.NormalizedTag> tags = QuestionTagNormalizer.normalizeAll(rawTags);
        List<Long> tagIds = new ArrayList<>(tags.size());

        for (QuestionTagNormalizer.NormalizedTag tag : tags) {
            MapSqlParameterSource parameters = ownership(scope, ownerOrganizationId)
                    .addValue("publicId", UUID.randomUUID().toString())
                    .addValue("tagCode", tag.slug().toUpperCase(java.util.Locale.ROOT).replace('-', '_'))
                    .addValue("tagName", tag.displayName())
                    .addValue("displayName", tag.displayName())
                    .addValue("normalizedName", tag.normalizedName())
                    .addValue("slug", tag.slug())
                    .addValue("createdBy", actorUserId)
                    .addValue("createdAt", occurredAt);
            jdbc.update("""
                    MERGE INTO QUESTION_TAG target
                    USING (
                        SELECT :publicId PUBLIC_ID,
                               :tagCode TAG_CODE,
                               :tagName TAG_NAME,
                               'ACTIVE' STATUS,
                               :displayName DISPLAY_NAME,
                               :normalizedName NORMALIZED_NAME,
                               :slug SLUG,
                               :scope CONTENT_SCOPE,
                               :ownerOrganizationId OWNER_ORGANIZATION_ID,
                               :createdBy CREATED_BY,
                               :createdAt CREATED_AT
                          FROM DUAL
                    ) source
                       ON (
                           target.CONTENT_SCOPE = source.CONTENT_SCOPE
                           AND NVL(target.OWNER_ORGANIZATION_ID, -1) =
                               NVL(source.OWNER_ORGANIZATION_ID, -1)
                           AND target.NORMALIZED_NAME = source.NORMALIZED_NAME
                       )
                    WHEN NOT MATCHED THEN INSERT (
                        PUBLIC_ID, TAG_CODE, TAG_NAME, STATUS,
                        DISPLAY_NAME, NORMALIZED_NAME, SLUG,
                        CONTENT_SCOPE, OWNER_ORGANIZATION_ID, CREATED_BY, CREATED_AT
                    ) VALUES (
                        source.PUBLIC_ID, source.TAG_CODE, source.TAG_NAME, source.STATUS,
                        source.DISPLAY_NAME, source.NORMALIZED_NAME, source.SLUG,
                        source.CONTENT_SCOPE, source.OWNER_ORGANIZATION_ID,
                        source.CREATED_BY, source.CREATED_AT
                    )
                    """, parameters);

            Long tagId = jdbc.queryForObject("""
                    SELECT TAG_ID
                      FROM QUESTION_TAG
                     WHERE CONTENT_SCOPE = :scope
                       AND NVL(OWNER_ORGANIZATION_ID, -1) = NVL(:ownerOrganizationId, -1)
                       AND NORMALIZED_NAME = :normalizedName
                    """, parameters, Long.class);
            if (tagId != null) {
                tagIds.add(tagId);
            }
        }

        jdbc.update("DELETE FROM QUESTION_TAG_RELATION WHERE QUESTION_ID = :questionId",
                Map.of("questionId", questionId));

        for (Long tagId : tagIds) {
            jdbc.update("""
                    INSERT INTO QUESTION_TAG_RELATION (
                        QUESTION_ID, TAG_ID, CREATED_AT, CREATED_BY
                    ) VALUES (
                        :questionId, :tagId, :createdAt, :createdBy
                    )
                    """, new MapSqlParameterSource()
                    .addValue("questionId", questionId)
                    .addValue("tagId", tagId)
                    .addValue("createdAt", occurredAt)
                    .addValue("createdBy", actorUserId));
        }
    }

    public Map<Long, List<QuestionTagView>> findByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<QuestionTagView>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT relation.QUESTION_ID,
                       tag.PUBLIC_ID,
                       tag.DISPLAY_NAME,
                       tag.SLUG
                  FROM QUESTION_TAG_RELATION relation
                  JOIN QUESTION_TAG tag ON tag.TAG_ID = relation.TAG_ID
                 WHERE relation.QUESTION_ID IN (:questionIds)
                 ORDER BY relation.QUESTION_ID, tag.DISPLAY_NAME
                """, Map.of("questionIds", questionIds), rs -> {
            long questionId = rs.getLong("QUESTION_ID");
            result.computeIfAbsent(questionId, ignored -> new ArrayList<>())
                    .add(mapView(rs));
        });
        result.replaceAll((ignored, tags) -> List.copyOf(tags));
        return Map.copyOf(result);
    }

    public List<QuestionTagView> findByQuestionId(long questionId) {
        return findByQuestionIds(List.of(questionId)).getOrDefault(questionId, List.of());
    }

    public List<QuestionTagView> suggest(ContentScope scope, Long ownerOrganizationId,
            String query, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 10);
        String normalizedQuery = QuestionTagNormalizer.searchValue(query);
        MapSqlParameterSource parameters = ownership(scope, ownerOrganizationId)
                .addValue("query", normalizedQuery.isBlank() ? null : "%" + normalizedQuery + "%")
                .addValue("prefix", normalizedQuery.isBlank() ? null : normalizedQuery + "%")
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT PUBLIC_ID, DISPLAY_NAME, SLUG
                  FROM (
                    SELECT PUBLIC_ID, DISPLAY_NAME, SLUG
                      FROM QUESTION_TAG
                     WHERE CONTENT_SCOPE = :scope
                       AND NVL(OWNER_ORGANIZATION_ID, -1) = NVL(:ownerOrganizationId, -1)
                       AND (:query IS NULL OR NORMALIZED_NAME LIKE :query)
                     ORDER BY CASE
                                  WHEN :prefix IS NOT NULL AND NORMALIZED_NAME LIKE :prefix THEN 0
                                  ELSE 1
                              END,
                              DISPLAY_NAME
                  )
                 WHERE ROWNUM <= :limit
                """, parameters, (rs, rowNum) -> mapView(rs));
    }

    public void copy(long sourceQuestionId, long targetQuestionId, ContentScope targetScope,
            Long targetOwnerOrganizationId, Long actorUserId, Instant occurredAt) {
        List<String> sourceTags = findByQuestionId(sourceQuestionId).stream()
                .map(QuestionTagView::displayName)
                .toList();
        replace(targetQuestionId, sourceTags, targetScope, targetOwnerOrganizationId,
                actorUserId, occurredAt);
    }

    private MapSqlParameterSource ownership(ContentScope scope, Long ownerOrganizationId) {
        return new MapSqlParameterSource()
                .addValue("scope", scope.name())
                .addValue("ownerOrganizationId", ownerOrganizationId);
    }

    private QuestionTagView mapView(ResultSet rs) throws SQLException {
        return new QuestionTagView(rs.getString("PUBLIC_ID"),
                rs.getString("DISPLAY_NAME"), rs.getString("SLUG"));
    }
}
