package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.domain.model.QuestionTechnologyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataQuestionTechnologyRepository
        extends JpaRepository<QuestionTechnologyJpaEntity, Long> {
    Optional<QuestionTechnologyJpaEntity> findByPublicId(String publicId);
    Optional<QuestionTechnologyJpaEntity> findByCodeIgnoreCase(String code);
    List<QuestionTechnologyJpaEntity> findByStatusOrderByDisplayOrderAscNameAsc(QuestionTechnologyStatus status);
    List<QuestionTechnologyJpaEntity> findAllByOrderByDisplayOrderAscNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
