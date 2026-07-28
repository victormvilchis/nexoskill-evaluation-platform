package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.QuestionSearchFilter;
import com.nexoskill.evaluation.questionbank.application.service.QuestionTagNormalizer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionGovernanceSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public QuestionGovernanceSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchPage search(QuestionSearchFilter filter, TenantContext tenant, int page, int size) {
        boolean globalAdministrator = tenant != null && tenant.globalAdministrator();
        ContentScope effectiveScope = filter.scope();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        addTenant(where, params, tenant, globalAdministrator);
        addFilters(where, params, filter, effectiveScope);
        String from = """
             FROM QUESTION q
             JOIN ORGANIZATION owner_org ON owner_org.ORGANIZATION_ID = q.OWNER_ORGANIZATION_ID
             LEFT JOIN QUESTION_TECHNOLOGY tech ON tech.TECHNOLOGY_ID = q.TECHNOLOGY_ID
             LEFT JOIN APP_USER creator ON creator.USER_ID = q.CREATED_BY
             LEFT JOIN QUESTION source_q ON source_q.QUESTION_ID = q.SOURCE_ORGANIZATION_QUESTION_ID
             LEFT JOIN ORGANIZATION source_org ON source_org.ORGANIZATION_ID = q.SOURCE_ORGANIZATION_ID
            """;
        String select = """
            SELECT q.QUESTION_ID,
                   owner_org.PUBLIC_ID OWNER_PUBLIC_ID,
                   owner_org.ORGANIZATION_CODE OWNER_CODE,
                   owner_org.ORGANIZATION_NAME OWNER_NAME,
                   tech.PUBLIC_ID TECHNOLOGY_PUBLIC_ID,
                   tech.TECHNOLOGY_CODE TECHNOLOGY_CODE,
                   tech.TECHNOLOGY_NAME TECHNOLOGY_NAME,
                   creator.PUBLIC_ID CREATOR_PUBLIC_ID,
                   creator.DISPLAY_NAME CREATOR_NAME,
                   source_org.PUBLIC_ID SOURCE_ORGANIZATION_PUBLIC_ID,
                   source_org.ORGANIZATION_NAME SOURCE_ORGANIZATION_NAME,
                   source_q.PUBLIC_ID SOURCE_QUESTION_PUBLIC_ID
            """;
        String order = " ORDER BY NVL(q.UPDATED_AT, q.CREATED_AT) DESC, q.QUESTION_ID DESC ";
        params.addValue("offsetRows", page * size);
        params.addValue("pageSize", size);
        List<SearchRow> rows = jdbc.query(select + from + where + order
                        + " OFFSET :offsetRows ROWS FETCH NEXT :pageSize ROWS ONLY ",
                params, this::mapRow);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, params, Long.class);
        return new SearchPage(rows, total == null ? 0 : total);
    }

    public List<Integer> creationYears(TenantContext tenant) {
        boolean globalAdministrator = tenant != null && tenant.globalAdministrator();
        StringBuilder where = new StringBuilder(" WHERE q.STATUS <> 'DELETED' ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        addTenant(where, params, tenant, globalAdministrator);
        return jdbc.query("""
                SELECT DISTINCT EXTRACT(YEAR FROM q.CREATED_AT) CREATION_YEAR
                  FROM QUESTION q
                """ + where + " ORDER BY CREATION_YEAR DESC ", params,
                (rs, rowNum) -> rs.getInt("CREATION_YEAR"));
    }

    public SearchRow metadata(Long questionId) {
        String sql = """
            SELECT q.QUESTION_ID,
                   owner_org.PUBLIC_ID OWNER_PUBLIC_ID,
                   owner_org.ORGANIZATION_CODE OWNER_CODE,
                   owner_org.ORGANIZATION_NAME OWNER_NAME,
                   tech.PUBLIC_ID TECHNOLOGY_PUBLIC_ID,
                   tech.TECHNOLOGY_CODE TECHNOLOGY_CODE,
                   tech.TECHNOLOGY_NAME TECHNOLOGY_NAME,
                   creator.PUBLIC_ID CREATOR_PUBLIC_ID,
                   creator.DISPLAY_NAME CREATOR_NAME,
                   source_org.PUBLIC_ID SOURCE_ORGANIZATION_PUBLIC_ID,
                   source_org.ORGANIZATION_NAME SOURCE_ORGANIZATION_NAME,
                   source_q.PUBLIC_ID SOURCE_QUESTION_PUBLIC_ID
              FROM QUESTION q
              JOIN ORGANIZATION owner_org ON owner_org.ORGANIZATION_ID = q.OWNER_ORGANIZATION_ID
              LEFT JOIN QUESTION_TECHNOLOGY tech ON tech.TECHNOLOGY_ID = q.TECHNOLOGY_ID
              LEFT JOIN APP_USER creator ON creator.USER_ID = q.CREATED_BY
              LEFT JOIN QUESTION source_q ON source_q.QUESTION_ID = q.SOURCE_ORGANIZATION_QUESTION_ID
              LEFT JOIN ORGANIZATION source_org ON source_org.ORGANIZATION_ID = q.SOURCE_ORGANIZATION_ID
             WHERE q.QUESTION_ID = :questionId
            """;
        List<SearchRow> rows = jdbc.query(sql, Map.of("questionId", questionId), this::mapRow);
        return rows.isEmpty() ? SearchRow.empty(questionId) : rows.getFirst();
    }

    private void addTenant(StringBuilder where, MapSqlParameterSource params,
                           TenantContext tenant, boolean globalAdministrator) {
        if (globalAdministrator) return;
        if (tenant == null || !tenant.hasOrganization()) {
            where.append(" AND 1 = 0 ");
            return;
        }
        where.append("""
            AND (
                (q.CONTENT_SCOPE = 'ORGANIZATION'
                    AND q.OWNER_ORGANIZATION_ID = :tenantOrganizationId)
                OR (
                    q.CONTENT_SCOPE = 'GLOBAL'
                    AND (
                        NVL(q.AVAILABILITY_MODE, 'GLOBAL') = 'GLOBAL'
                        OR EXISTS (
                            SELECT 1
                              FROM QUESTION_ORGANIZATION_AVAILABILITY availability
                             WHERE availability.QUESTION_ID = q.QUESTION_ID
                               AND availability.ORGANIZATION_ID = :tenantOrganizationId
                               AND availability.STATUS = 'ACTIVE'
                        )
                    )
                )
            )
            """);
        params.addValue("tenantOrganizationId", tenant.organizationId());
    }

    private void addFilters(StringBuilder where, MapSqlParameterSource params,
                            QuestionSearchFilter filter, ContentScope scope) {
        if (filter.query() != null && !filter.query().isBlank()) {
            where.append("""
                AND (
                    LOWER(q.PUBLIC_ID) LIKE :query
                    OR TO_CHAR(q.QUESTION_ID) LIKE :query
                    OR LOWER(DBMS_LOB.SUBSTR(q.STATEMENT_TEXT, 4000, 1)) LIKE :query
                    OR LOWER(DBMS_LOB.SUBSTR(q.CODE_CONTENT, 4000, 1)) LIKE :query
                    OR LOWER(owner_org.ORGANIZATION_NAME) LIKE :query
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_CATEGORY_RELATION qcr_search
                          JOIN QUESTION_CATEGORY qc_search
                            ON qc_search.CATEGORY_ID = qcr_search.CATEGORY_ID
                         WHERE qcr_search.QUESTION_ID = q.QUESTION_ID
                           AND LOWER(qc_search.CATEGORY_NAME) LIKE :query
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM QUESTION_TAG_RELATION qtr_search
                          JOIN QUESTION_TAG qt_search
                            ON qt_search.TAG_ID = qtr_search.TAG_ID
                         WHERE qtr_search.QUESTION_ID = q.QUESTION_ID
                           AND (qt_search.NORMALIZED_NAME LIKE :tagQuery
                                OR qt_search.SLUG LIKE :tagQuery)
                    )
                    OR EXISTS (
                        SELECT 1 FROM QUESTION_OPTION qo
                         WHERE qo.QUESTION_ID = q.QUESTION_ID
                           AND (LOWER(DBMS_LOB.SUBSTR(qo.OPTION_TEXT, 4000, 1)) LIKE :query
                                OR LOWER(DBMS_LOB.SUBSTR(qo.MATCH_TEXT, 4000, 1)) LIKE :query)
                    )
                )
                """);
            params.addValue("query", "%" + filter.query().trim().toLowerCase() + "%");
            params.addValue("tagQuery", "%" + QuestionTagNormalizer.searchValue(filter.query()) + "%");
        }
        if (filter.status() == null) where.append(" AND q.STATUS <> 'DELETED' ");
        else {
            where.append(" AND q.STATUS = :status ");
            params.addValue("status", filter.status().name());
        }
        optionalEquals(where, params, "q.TYPE_CODE", "typeCode", normalize(filter.typeCode()));
        optionalEquals(where, params, "q.DIFFICULTY_CODE", "difficultyCode", normalize(filter.difficultyCode()));
        optionalEquals(where, params, "q.CONTENT_SCOPE", "scope", scope == null ? null : scope.name());
        optionalEquals(where, params, "owner_org.PUBLIC_ID", "ownerPublicId", blankToNull(filter.organizationPublicId()));
        optionalEquals(where, params, "creator.PUBLIC_ID", "creatorPublicId", blankToNull(filter.creatorPublicId()));
        if (filter.categoryPublicId() != null && !filter.categoryPublicId().isBlank()) {
            where.append("""
                AND EXISTS (
                    SELECT 1
                      FROM QUESTION_CATEGORY_RELATION relation
                      JOIN QUESTION_CATEGORY category ON category.CATEGORY_ID = relation.CATEGORY_ID
                     WHERE relation.QUESTION_ID = q.QUESTION_ID
                       AND category.PUBLIC_ID = :categoryPublicId
                )
                """);
            params.addValue("categoryPublicId", filter.categoryPublicId().trim());
        }
        dateFrom(where, params, "q.CREATED_AT", "createdFrom", filter.createdFrom());
        dateTo(where, params, "q.CREATED_AT", "createdTo", filter.createdTo());
        dateFrom(where, params, "NVL(q.UPDATED_AT, q.CREATED_AT)", "updatedFrom", filter.updatedFrom());
        dateTo(where, params, "NVL(q.UPDATED_AT, q.CREATED_AT)", "updatedTo", filter.updatedTo());
        if (filter.clonedToGlobal() != null) {
            where.append(filter.clonedToGlobal()
                    ? " AND q.SOURCE_ORGANIZATION_QUESTION_ID IS NOT NULL "
                    : " AND q.SOURCE_ORGANIZATION_QUESTION_ID IS NULL ");
        }
        if (filter.inUse() != null) {
            String usage = """
                EXISTS (SELECT 1 FROM FORM_QUESTION fq WHERE fq.QUESTION_ID = q.QUESTION_ID)
                OR EXISTS (SELECT 1 FROM COLLECTION_QUESTION_RELATION cqr WHERE cqr.QUESTION_ID = q.QUESTION_ID)
                """;
            where.append(filter.inUse() ? " AND (" + usage + ") " : " AND NOT (" + usage + ") ");
        }
    }

    private void optionalEquals(StringBuilder where, MapSqlParameterSource params,
                                String column, String parameter, String value) {
        if (value == null) return;
        where.append(" AND ").append(column).append(" = :").append(parameter).append(' ');
        params.addValue(parameter, value);
    }

    private void dateFrom(StringBuilder where, MapSqlParameterSource params,
                          String column, String parameter, LocalDate value) {
        if (value == null) return;
        where.append(" AND ").append(column).append(" >= :").append(parameter).append(' ');
        params.addValue(parameter, value.atStartOfDay());
    }

    private void dateTo(StringBuilder where, MapSqlParameterSource params,
                        String column, String parameter, LocalDate value) {
        if (value == null) return;
        where.append(" AND ").append(column).append(" < :").append(parameter).append(' ');
        params.addValue(parameter, value.plusDays(1).atStartOfDay());
    }

    private SearchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SearchRow(rs.getLong("QUESTION_ID"),
                rs.getString("OWNER_PUBLIC_ID"), rs.getString("OWNER_CODE"), rs.getString("OWNER_NAME"),
                rs.getString("TECHNOLOGY_PUBLIC_ID"), rs.getString("TECHNOLOGY_CODE"),
                rs.getString("TECHNOLOGY_NAME"), rs.getString("CREATOR_PUBLIC_ID"),
                rs.getString("CREATOR_NAME"), rs.getString("SOURCE_ORGANIZATION_PUBLIC_ID"),
                rs.getString("SOURCE_ORGANIZATION_NAME"), rs.getString("SOURCE_QUESTION_PUBLIC_ID"));
    }

    private String normalize(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SearchPage(List<SearchRow> rows, long totalElements) {
        public SearchPage { rows = List.copyOf(rows); }
    }

    public record SearchRow(Long questionId,
                            String ownerOrganizationPublicId,
                            String ownerOrganizationCode,
                            String ownerOrganizationName,
                            String technologyPublicId,
                            String technologyCode,
                            String technologyName,
                            String creatorPublicId,
                            String creatorName,
                            String sourceOrganizationPublicId,
                            String sourceOrganizationName,
                            String sourceQuestionPublicId) {
        static SearchRow empty(Long questionId) {
            return new SearchRow(questionId, null, null, null, null, null, null,
                    null, null, null, null, null);
        }
    }
}
