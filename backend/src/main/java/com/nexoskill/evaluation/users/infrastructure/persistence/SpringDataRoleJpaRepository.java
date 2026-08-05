package com.nexoskill.evaluation.users.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataRoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {

    Optional<RoleJpaEntity> findByCode(String code);

    Optional<RoleJpaEntity> findByCodeIgnoreCase(String code);

    List<RoleJpaEntity> findByStatusOrderByNameAsc(String status);

    List<RoleJpaEntity> findAllByOrderByNameAsc();

    @Query(value = """
            select r
              from RoleJpaEntity r
             where r.code <> 'USER'
               and (:query is null or lower(r.name) like concat(concat('%', :query), '%'))
               and (:status is null or r.status = :status)
            """, countQuery = """
            select count(r)
              from RoleJpaEntity r
             where r.code <> 'USER'
               and (:query is null or lower(r.name) like concat(concat('%', :query), '%'))
               and (:status is null or r.status = :status)
            """)
    Page<RoleJpaEntity> search(@Param("query") String query, @Param("status") String status, Pageable pageable);

    @Query("""
            select count(r)
              from RoleJpaEntity r
             where upper(trim(r.name)) = upper(trim(:name))
               and (:excludedId is null or r.id <> :excludedId)
            """)
    long countByNormalizedName(@Param("name") String name, @Param("excludedId") Long excludedId);
}
