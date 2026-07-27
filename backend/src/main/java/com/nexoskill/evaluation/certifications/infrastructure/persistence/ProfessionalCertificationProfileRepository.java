package com.nexoskill.evaluation.certifications.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalCertificationProfileRepository extends JpaRepository<ProfessionalCertificationProfileJpaEntity, Long> {
    Optional<ProfessionalCertificationProfileJpaEntity> findByPublicIdAndStatus(String publicId, String status);
    Optional<ProfessionalCertificationProfileJpaEntity> findByPublicId(String publicId);
    List<ProfessionalCertificationProfileJpaEntity> findAllByStatusOrderBySortOrderAscNameAsc(String status);
    List<ProfessionalCertificationProfileJpaEntity> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
