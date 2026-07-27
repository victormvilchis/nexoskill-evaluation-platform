package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnologicalProfileCatalogRepository
        extends JpaRepository<TechnologicalProfileCatalogJpaEntity, Long> {
    Optional<TechnologicalProfileCatalogJpaEntity> findByPublicId(String publicId);
    Optional<TechnologicalProfileCatalogJpaEntity> findByCode(String code);
    List<TechnologicalProfileCatalogJpaEntity> findAllByStatusOrderByDisplayOrderAscNameAsc(String status);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
