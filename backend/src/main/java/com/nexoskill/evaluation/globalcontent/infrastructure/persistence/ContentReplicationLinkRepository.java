package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReplicationLinkRepository extends JpaRepository<ContentReplicationLinkJpaEntity, Long> {
	Optional<ContentReplicationLinkJpaEntity> findByOrganizationIdAndContentTypeAndSourceGlobalContentIdAndSourceGlobalVersion(
			Long organizationId, GlobalContentType contentType, Long sourceGlobalContentId, long sourceGlobalVersion);

	Optional<ContentReplicationLinkJpaEntity> findByOrganizationIdAndContentTypeAndTargetContentId(Long organizationId,
			GlobalContentType contentType, Long targetContentId);
}
