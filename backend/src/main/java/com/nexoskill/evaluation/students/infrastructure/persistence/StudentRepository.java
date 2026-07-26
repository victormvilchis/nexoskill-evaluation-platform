package com.nexoskill.evaluation.students.infrastructure.persistence;

import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<StudentJpaEntity, Long> {
    Optional<StudentJpaEntity> findByPublicId(String publicId);

    Optional<StudentJpaEntity> findByOrganizationIdAndPublicId(Long organizationId, String publicId);

    Optional<StudentJpaEntity> findByOrganizationIdAndNormalizedEmail(Long organizationId, String normalizedEmail);

    boolean existsByOrganizationIdAndNormalizedEmail(Long organizationId, String normalizedEmail);

    boolean existsByOrganizationIdAndStudentCode(Long organizationId, String studentCode);

    boolean existsByOrganizationId(Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudentJpaEntity s where s.organizationId = :organizationId and s.normalizedEmail = :normalizedEmail")
    Optional<StudentJpaEntity> findForLogin(@Param("organizationId") Long organizationId,
            @Param("normalizedEmail") String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudentJpaEntity s where s.id = :id")
    Optional<StudentJpaEntity> findByIdForUpdate(@Param("id") Long id);


    @Query("""
        select s.id from StudentJpaEntity s
        where s.status = com.nexoskill.evaluation.students.domain.StudentStatus.ACTIVE
          and s.expiresAt is not null
          and s.expiresAt <= :now
        """)
    List<Long> findExpiredActiveStudentIds(@Param("now") Instant now);

    @Query("""
        select s from StudentJpaEntity s
        where s.organizationId = :organizationId
          and (:includeDeleted = true
               or :status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.DELETED
               or s.status <> com.nexoskill.evaluation.students.domain.StudentStatus.DELETED)
          and (
               :status is null
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.ACTIVE
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.ACTIVE
                   and s.validFrom <= :now and (s.expiresAt is null or s.expiresAt > :now))
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.PENDING
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.ACTIVE
                   and s.validFrom > :now)
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.EXPIRED
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.ACTIVE
                   and s.expiresAt is not null and s.expiresAt <= :now)
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.INACTIVE
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.INACTIVE)
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.SUSPENDED
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.SUSPENDED)
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.ARCHIVED
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.ARCHIVED)
               or (:status = com.nexoskill.evaluation.students.domain.StudentEffectiveStatus.DELETED
                   and s.status = com.nexoskill.evaluation.students.domain.StudentStatus.DELETED)
          )
          and (:query is null
               or lower(s.displayName) like lower(concat('%', :query, '%'))
               or lower(s.email) like lower(concat('%', :query, '%'))
               or lower(s.studentCode) like lower(concat('%', :query, '%')))
        """)
    Page<StudentJpaEntity> search(@Param("organizationId") Long organizationId,
            @Param("query") String query,
            @Param("status") StudentEffectiveStatus status,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("now") Instant now,
            Pageable pageable);
}
