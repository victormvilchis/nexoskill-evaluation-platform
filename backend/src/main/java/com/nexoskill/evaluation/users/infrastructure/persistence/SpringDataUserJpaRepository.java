package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserJpaRepository
        extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);

    @Query(
            value = """
                    SELECT u
                    FROM UserJpaEntity u
                    WHERE (
                        :query IS NULL
                        OR LOWER(u.email) LIKE CONCAT(CONCAT('%', :query), '%')
                        OR LOWER(u.displayName) LIKE CONCAT(CONCAT('%', :query), '%')
                        OR LOWER(u.firstName) LIKE CONCAT(CONCAT('%', :query), '%')
                        OR LOWER(u.lastName) LIKE CONCAT(CONCAT('%', :query), '%')
                    )
                    AND (:status IS NULL OR u.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(u)
                    FROM UserJpaEntity u
                    WHERE (
                        :query IS NULL
                        OR LOWER(u.email) LIKE CONCAT(CONCAT('%', :query), '%')
                        OR LOWER(u.displayName) LIKE CONCAT(CONCAT('%', :query), '%')
                        OR LOWER(u.firstName) LIKE CONCAT(CONCAT('%', :query), '%')
                        OR LOWER(u.lastName) LIKE CONCAT(CONCAT('%', :query), '%')
                    )
                    AND (:status IS NULL OR u.status = :status)
                    """
    )
    Page<UserJpaEntity> search(
            @Param("query") String query,
            @Param("status") UserStatus status,
            Pageable pageable
    );
}
