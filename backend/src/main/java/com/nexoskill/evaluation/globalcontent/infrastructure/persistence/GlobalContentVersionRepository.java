package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalContentVersionRepository extends JpaRepository<GlobalContentVersionJpaEntity, Long> {
	Optional<GlobalContentVersionJpaEntity> findByContentTypeAndContentIdAndVersionNumber(GlobalContentType contentType,
			Long contentId, long versionNumber);

	Optional<GlobalContentVersionJpaEntity> findFirstByContentTypeAndContentIdOrderByVersionNumberDesc(
			GlobalContentType contentType, Long contentId);

	Optional<GlobalContentVersionJpaEntity> findFirstByContentTypeAndContentIdAndStatusOrderByVersionNumberDesc(
			GlobalContentType contentType, Long contentId, EditorialStatus status);

	List<GlobalContentVersionJpaEntity> findAllByContentTypeAndContentIdOrderByVersionNumberDesc(
			GlobalContentType contentType, Long contentId);

	boolean existsByContentTypeAndContentId(GlobalContentType contentType, Long contentId);
}
