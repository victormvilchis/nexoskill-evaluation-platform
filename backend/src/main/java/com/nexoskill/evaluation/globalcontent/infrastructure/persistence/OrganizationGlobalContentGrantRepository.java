package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.DistributionMode;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import com.nexoskill.evaluation.globalcontent.domain.model.GrantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationGlobalContentGrantRepository
		extends JpaRepository<OrganizationGlobalContentGrantJpaEntity, Long> {
	Optional<OrganizationGlobalContentGrantJpaEntity> findByPublicId(String publicId);

	Optional<OrganizationGlobalContentGrantJpaEntity> findByOrganizationIdAndContentTypeAndGlobalContentIdAndGlobalVersionAndDistributionMode(
			Long organizationId, GlobalContentType contentType, Long globalContentId, long globalVersion,
			DistributionMode distributionMode);

	boolean existsByOrganizationIdAndContentTypeAndGlobalContentIdAndStatus(Long organizationId,
			GlobalContentType contentType, Long globalContentId, GrantStatus status);

	List<OrganizationGlobalContentGrantJpaEntity> findAllByOrganizationIdAndStatusOrderByEnabledAtDesc(
			Long organizationId, GrantStatus status);

	List<OrganizationGlobalContentGrantJpaEntity> findAllByOrganizationIdAndContentTypeAndGlobalContentIdAndStatus(
			Long organizationId, GlobalContentType contentType, Long globalContentId, GrantStatus status);

	List<OrganizationGlobalContentGrantJpaEntity> findAllByContentTypeAndGlobalContentIdAndStatus(
			GlobalContentType contentType, Long globalContentId, GrantStatus status);
}
