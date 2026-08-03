package com.nexoskill.evaluation.forms.infrastructure;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface FormRepository extends JpaRepository<FormJpaEntity, Long> {
    Optional<FormJpaEntity> findByPublicId(String publicId);
    Optional<FormJpaEntity> findByCreateOperationId(String createOperationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select form from FormJpaEntity form where form.publicId = :publicId")
    Optional<FormJpaEntity> findByPublicIdForUpdate(@Param("publicId") String publicId);
    boolean existsByCode(String code);
    List<FormJpaEntity> findAllByStatus(String status);

    @Query(value = """
            select form
              from FormJpaEntity form
             where (
                    :globalContext = true
                    or (form.contentScope = :organizationScope and form.ownerOrganizationId = :organizationId)
                    or (form.contentScope = :globalScope
                        and (
                            :allowAllGlobal = true
                            or exists (
                                select grant.id from OrganizationGlobalContentGrantJpaEntity grant
                                 where grant.organizationId = :organizationId
                                   and grant.contentType = :contentType
                                   and grant.globalContentId = form.id
                                   and grant.status = :grantStatus
                                   and grant.distributionMode = :distributionMode
                                   and (grant.availableFrom is null or grant.availableFrom <= :now)
                                   and (grant.expiresAt is null or grant.expiresAt > :now)
                            )
                        )
                        and (
                            not exists (
                                select version.id from GlobalContentVersionJpaEntity version
                                 where version.contentType = :contentType and version.contentId = form.id
                            )
                            or exists (
                                select published.id from GlobalContentVersionJpaEntity published
                                 where published.contentType = :contentType
                                   and published.contentId = form.id
                                   and published.status = :publishedStatus
                            )
                        )
                    )
                  )
               and (:status is null or form.status = :status)
               and (:mode is null or form.modeCode = :mode)
               and (:query is null or lower(form.title) like :query or lower(form.code) like :query)
            """,
            countQuery = """
            select count(form.id)
              from FormJpaEntity form
             where (
                    :globalContext = true
                    or (form.contentScope = :organizationScope and form.ownerOrganizationId = :organizationId)
                    or (form.contentScope = :globalScope
                        and (
                            :allowAllGlobal = true
                            or exists (
                                select grant.id from OrganizationGlobalContentGrantJpaEntity grant
                                 where grant.organizationId = :organizationId
                                   and grant.contentType = :contentType
                                   and grant.globalContentId = form.id
                                   and grant.status = :grantStatus
                                   and grant.distributionMode = :distributionMode
                                   and (grant.availableFrom is null or grant.availableFrom <= :now)
                                   and (grant.expiresAt is null or grant.expiresAt > :now)
                            )
                        )
                        and (
                            not exists (
                                select version.id from GlobalContentVersionJpaEntity version
                                 where version.contentType = :contentType and version.contentId = form.id
                            )
                            or exists (
                                select published.id from GlobalContentVersionJpaEntity published
                                 where published.contentType = :contentType
                                   and published.contentId = form.id
                                   and published.status = :publishedStatus
                            )
                        )
                    )
                  )
               and (:status is null or form.status = :status)
               and (:mode is null or form.modeCode = :mode)
               and (:query is null or lower(form.title) like :query or lower(form.code) like :query)
            """)
    Page<FormJpaEntity> searchVisible(
            @Param("query") String query,
            @Param("status") String status,
            @Param("mode") String mode,
            @Param("globalContext") boolean globalContext,
            @Param("organizationId") Long organizationId,
            @Param("allowAllGlobal") boolean allowAllGlobal,
            @Param("organizationScope") ContentScope organizationScope,
            @Param("globalScope") ContentScope globalScope,
            @Param("contentType") GlobalContentType contentType,
            @Param("grantStatus") GrantStatus grantStatus,
            @Param("distributionMode") DistributionMode distributionMode,
            @Param("publishedStatus") EditorialStatus publishedStatus,
            @Param("now") Instant now,
            Pageable pageable);
}
