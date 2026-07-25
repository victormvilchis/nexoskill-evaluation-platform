package com.nexoskill.evaluation.authentication.infrastructure.persistence;

import com.nexoskill.evaluation.authentication.domain.model.SessionStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAuthSessionRepository extends JpaRepository<AuthSessionJpaEntity, Long> {

	Optional<AuthSessionJpaEntity> findByTokenHash(String tokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE AuthSessionJpaEntity session
			   SET session.status = :revokedStatus,
			       session.revokedAt = :revokedAt
			 WHERE session.userId = :userId
			   AND session.status = :activeStatus
			""")
	int revokeActiveSessions(@Param("userId") Long userId, @Param("activeStatus") SessionStatus activeStatus,
			@Param("revokedStatus") SessionStatus revokedStatus, @Param("revokedAt") Instant revokedAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE AuthSessionJpaEntity session
			   SET session.status = :revokedStatus,
			       session.revokedAt = :revokedAt
			 WHERE session.userId = :userId
			   AND session.status = :activeStatus
			   AND session.tokenHash <> :currentTokenHash
			""")
	int revokeOtherActiveSessions(@Param("userId") Long userId, @Param("currentTokenHash") String currentTokenHash,
			@Param("activeStatus") SessionStatus activeStatus, @Param("revokedStatus") SessionStatus revokedStatus,
			@Param("revokedAt") Instant revokedAt);

}
