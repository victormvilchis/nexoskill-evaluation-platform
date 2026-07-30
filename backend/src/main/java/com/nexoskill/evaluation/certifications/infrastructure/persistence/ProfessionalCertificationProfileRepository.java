package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalCertificationProfileRepository extends JpaRepository<ProfessionalCertificationProfileJpaEntity, Long> {
    Optional<ProfessionalCertificationProfileJpaEntity> findByPublicIdAndStatus(String publicId, String status);
    Optional<ProfessionalCertificationProfileJpaEntity> findByPublicId(String publicId);
    List<ProfessionalCertificationProfileJpaEntity> findAllByStatusOrderBySortOrderAscNameAsc(String status);
    List<ProfessionalCertificationProfileJpaEntity> findAllByOrderBySortOrderAscNameAsc();

    @Query("""
            select p from ProfessionalCertificationProfileJpaEntity p
            where (:status is null or p.status = :status)
              and (p.contentScope = com.nexoskill.evaluation.organizations.domain.model.ContentScope.GLOBAL
                   or p.ownerOrganizationId = :organizationId)
            order by p.sortOrder asc, p.name asc
            """)
    List<ProfessionalCertificationProfileJpaEntity> findVisible(@Param("organizationId") Long organizationId,
            @Param("status") String status);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
