package com.nexoskill.evaluation.users.infrastructure.persistence;

import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserJpaRepository extends JpaRepository<UserJpaEntity, Long> {
	Optional<UserJpaEntity> findByNormalizedEmail(String normalizedEmail);

	boolean existsByNormalizedEmail(String normalizedEmail);

	boolean existsByNormalizedEmailAndPublicIdNot(String normalizedEmail, String publicId);

	Optional<UserJpaEntity> findByPublicId(String publicId);

	@Query(value = """
			SELECT DISTINCT u
			FROM UserJpaEntity u
			JOIN u.roles r
			WHERE r.code IN ('ADMINISTRATOR', 'MANAGER', 'SUPERVISOR')
			  AND (
			    :query IS NULL
			    OR LOWER(u.email) LIKE CONCAT(CONCAT('%', :query), '%')
			    OR LOWER(u.displayName) LIKE CONCAT(CONCAT('%', :query), '%')
			    OR LOWER(u.firstName) LIKE CONCAT(CONCAT('%', :query), '%')
			    OR LOWER(u.lastName) LIKE CONCAT(CONCAT('%', :query), '%')
			  )
			  AND (:status IS NULL OR u.status = :status)
			""", countQuery = """
			SELECT COUNT(DISTINCT u)
			FROM UserJpaEntity u
			JOIN u.roles r
			WHERE r.code IN ('ADMINISTRATOR', 'MANAGER', 'SUPERVISOR')
			  AND (
			    :query IS NULL
			    OR LOWER(u.email) LIKE CONCAT(CONCAT('%', :query), '%')
			    OR LOWER(u.displayName) LIKE CONCAT(CONCAT('%', :query), '%')
			    OR LOWER(u.firstName) LIKE CONCAT(CONCAT('%', :query), '%')
			    OR LOWER(u.lastName) LIKE CONCAT(CONCAT('%', :query), '%')
			  )
			  AND (:status IS NULL OR u.status = :status)
			""")
	Page<UserJpaEntity> search(@Param("query") String query, @Param("status") UserStatus status, Pageable pageable);

	@Query("""
			SELECT COUNT(DISTINCT u)
			FROM UserJpaEntity u
			JOIN u.roles r
			WHERE r.code = 'ADMINISTRATOR'
			  AND u.status = :userStatus
			  AND u.access.status = :accessStatus
			  AND u.access.startsAt <= :now
			  AND (u.access.expiresAt IS NULL OR u.access.expiresAt > :now)
			""")
	long countEffectiveAdministrators(@Param("now") java.time.Instant now, @Param("userStatus") UserStatus userStatus,
			@Param("accessStatus") UserAccessStatus accessStatus);
}
