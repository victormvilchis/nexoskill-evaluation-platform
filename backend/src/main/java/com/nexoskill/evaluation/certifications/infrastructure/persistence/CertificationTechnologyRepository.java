package com.nexoskill.evaluation.certifications.infrastructure.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CertificationTechnologyRepository extends JpaRepository<CertificationTechnologyJpaEntity, Long> {
    Optional<CertificationTechnologyJpaEntity> findByPublicIdAndStatus(String publicId, String status);
    List<CertificationTechnologyJpaEntity> findAllByStatusOrderBySortOrderAscNameAsc(String status);
}
