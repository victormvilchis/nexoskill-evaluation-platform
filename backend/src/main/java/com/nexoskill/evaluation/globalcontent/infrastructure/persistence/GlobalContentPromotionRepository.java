package com.nexoskill.evaluation.globalcontent.infrastructure.persistence;

import com.nexoskill.evaluation.globalcontent.domain.model.EditorialStatus;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalContentPromotionRepository extends JpaRepository<GlobalContentPromotionJpaEntity, Long> {
    Optional<GlobalContentPromotionJpaEntity> findByPublicId(String publicId);
    Optional<GlobalContentPromotionJpaEntity> findByContentTypeAndSourceContentIdAndSourceVersion(
            GlobalContentType contentType, Long sourceContentId, long sourceVersion);
    Optional<GlobalContentPromotionJpaEntity> findFirstByContentTypeAndSourceContentIdAndStatusOrderByPromotedAtDesc(
            GlobalContentType contentType, Long sourceContentId, EditorialStatus status);
    Optional<GlobalContentPromotionJpaEntity> findFirstByContentTypeAndGlobalContentIdAndStatusOrderByGlobalVersionDesc(
            GlobalContentType contentType, Long globalContentId, EditorialStatus status);
    List<GlobalContentPromotionJpaEntity> findAllByOrderByPromotedAtDesc();
}
