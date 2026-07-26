package com.nexoskill.evaluation.forms.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormRepository extends JpaRepository<FormJpaEntity, Long> {
    Optional<FormJpaEntity> findByPublicId(String publicId);
    boolean existsByCode(String code);
    List<FormJpaEntity> findAllByStatus(String status);
}
