package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import com.nexoskill.evaluation.organizations.domain.model.OrganizationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<OrganizationJpaEntity, Long> {
    Optional<OrganizationJpaEntity> findByPublicId(String publicId);
    Optional<OrganizationJpaEntity> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query(value = """
        select o as organization, count(s.id) as studentCount
          from OrganizationJpaEntity o
          left join StudentJpaEntity s
            on s.organizationId = o.id
           and s.status <> com.nexoskill.evaluation.students.domain.StudentStatus.DELETED
         where (:query is null or lower(o.name) like lower(concat('%', :query, '%'))
                or lower(o.code) like lower(concat('%', :query, '%')))
           and (:status is null or o.status = :status)
         group by o
        """, countQuery = """
        select count(o)
          from OrganizationJpaEntity o
         where (:query is null or lower(o.name) like lower(concat('%', :query, '%'))
                or lower(o.code) like lower(concat('%', :query, '%')))
           and (:status is null or o.status = :status)
        """)
    Page<OrganizationSearchRow> search(@Param("query") String query,
                                      @Param("status") OrganizationStatus status,
                                      Pageable pageable);
}
