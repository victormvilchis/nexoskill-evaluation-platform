package com.nexoskill.evaluation.collections.infrastructure;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningCollectionRepository extends JpaRepository<LearningCollectionJpaEntity, Long> {
	Optional<LearningCollectionJpaEntity> findByPublicId(String publicId);

	boolean existsByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select collection from LearningCollectionJpaEntity collection where collection.publicId = :publicId")
	Optional<LearningCollectionJpaEntity> findByPublicIdForUpdate(@Param("publicId") String publicId);

	@Query(value = """
			select collection
			  from LearningCollectionJpaEntity collection
			 where (
			        :globalContext = true
			        or (collection.contentScope = :organizationScope
			            and collection.ownerOrganizationId = :organizationId)
			        or (collection.contentScope = :globalScope
			            and (
			                :allowAllGlobal = true
			                or exists (
			                    select grant.id
			                      from OrganizationGlobalContentGrantJpaEntity grant
			                     where grant.organizationId = :organizationId
			                       and grant.contentType = :contentType
			                       and grant.globalContentId = collection.id
			                       and grant.status = :grantStatus
			                       and grant.distributionMode = :distributionMode
			                       and (grant.availableFrom is null or grant.availableFrom <= :now)
			                       and (grant.expiresAt is null or grant.expiresAt > :now)
			                )
			            )
			            and (
			                not exists (
			                    select version.id
			                      from GlobalContentVersionJpaEntity version
			                     where version.contentType = :contentType
			                       and version.contentId = collection.id
			                )
			                or exists (
			                    select published.id
			                      from GlobalContentVersionJpaEntity published
			                     where published.contentType = :contentType
			                       and published.contentId = collection.id
			                       and published.status = :publishedStatus
			                )
			            )
			        )
			      )
			   and (:status is null or collection.status = :status)
			   and (:query is null
			        or lower(collection.name) like :query
			        or lower(collection.code) like :query
			        or lower(coalesce(collection.description, '')) like :query)
			""", countQuery = """
			select count(collection.id)
			  from LearningCollectionJpaEntity collection
			 where (
			        :globalContext = true
			        or (collection.contentScope = :organizationScope
			            and collection.ownerOrganizationId = :organizationId)
			        or (collection.contentScope = :globalScope
			            and (
			                :allowAllGlobal = true
			                or exists (
			                    select grant.id
			                      from OrganizationGlobalContentGrantJpaEntity grant
			                     where grant.organizationId = :organizationId
			                       and grant.contentType = :contentType
			                       and grant.globalContentId = collection.id
			                       and grant.status = :grantStatus
			                       and grant.distributionMode = :distributionMode
			                       and (grant.availableFrom is null or grant.availableFrom <= :now)
			                       and (grant.expiresAt is null or grant.expiresAt > :now)
			                )
			            )
			            and (
			                not exists (
			                    select version.id
			                      from GlobalContentVersionJpaEntity version
			                     where version.contentType = :contentType
			                       and version.contentId = collection.id
			                )
			                or exists (
			                    select published.id
			                      from GlobalContentVersionJpaEntity published
			                     where published.contentType = :contentType
			                       and published.contentId = collection.id
			                       and published.status = :publishedStatus
			                )
			            )
			        )
			      )
			   and (:status is null or collection.status = :status)
			   and (:query is null
			        or lower(collection.name) like :query
			        or lower(collection.code) like :query
			        or lower(coalesce(collection.description, '')) like :query)
			""")
	Page<LearningCollectionJpaEntity> searchVisible(@Param("query") String query, @Param("status") String status,
			@Param("globalContext") boolean globalContext, @Param("organizationId") Long organizationId,
			@Param("allowAllGlobal") boolean allowAllGlobal, @Param("organizationScope") ContentScope organizationScope,
			@Param("globalScope") ContentScope globalScope, @Param("contentType") GlobalContentType contentType,
			@Param("grantStatus") GrantStatus grantStatus, @Param("distributionMode") DistributionMode distributionMode,
			@Param("publishedStatus") EditorialStatus publishedStatus, @Param("now") Instant now, Pageable pageable);
}
