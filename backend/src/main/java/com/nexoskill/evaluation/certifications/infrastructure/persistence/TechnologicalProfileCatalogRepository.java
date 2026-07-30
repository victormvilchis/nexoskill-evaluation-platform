package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnologicalProfileCatalogRepository
        extends JpaRepository<TechnologicalProfileCatalogJpaEntity, Long> {
    Optional<TechnologicalProfileCatalogJpaEntity> findByPublicId(String publicId);
    Optional<TechnologicalProfileCatalogJpaEntity> findByCode(String code);
    List<TechnologicalProfileCatalogJpaEntity> findAllByStatusOrderByDisplayOrderAscNameAsc(String status);

    @Query("""
            select p from TechnologicalProfileCatalogJpaEntity p
            where (:status is null or p.status = :status)
              and (p.contentScope = com.nexoskill.evaluation.organizations.domain.model.ContentScope.GLOBAL
                   or p.ownerOrganizationId = :organizationId)
            order by p.displayOrder asc, p.name asc
            """)
    List<TechnologicalProfileCatalogJpaEntity> findVisible(@Param("organizationId") Long organizationId,
            @Param("status") String status);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
