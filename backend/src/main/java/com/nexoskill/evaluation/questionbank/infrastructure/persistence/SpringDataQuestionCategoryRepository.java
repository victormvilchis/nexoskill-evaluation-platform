package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.CatalogStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataQuestionCategoryRepository
        extends JpaRepository<QuestionCategoryJpaEntity, Long> {

    List<QuestionCategoryJpaEntity> findAllByStatusOrderByNameAsc(CatalogStatus status);

    Optional<QuestionCategoryJpaEntity> findByPublicIdAndStatus(
            String publicId,
            CatalogStatus status
    );

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT COUNT(c) > 0 FROM QuestionCategoryJpaEntity c WHERE LOWER(c.name) = :name")
    boolean existsByNormalizedName(@Param("name") String normalizedName);
}
