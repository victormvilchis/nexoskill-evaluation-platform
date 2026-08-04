package com.nexoskill.evaluation.students.infrastructure.persistence;

import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentSessionRepository extends JpaRepository<StudentSessionJpaEntity, Long> {
	Optional<StudentSessionJpaEntity> findByTokenHash(String tokenHash);

	Optional<StudentSessionJpaEntity> findByStudentIdAndStatus(Long studentId, StudentSessionStatus status);

	Optional<StudentSessionJpaEntity> findByPublicIdAndStudentId(String publicId, Long studentId);

	List<StudentSessionJpaEntity> findByStudentIdOrderByCreatedAtDesc(Long studentId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update StudentSessionJpaEntity session
			   set session.status = :expired,
			       session.revokedAt = :now,
			       session.revocationReason = :reason
			 where session.status = :active
			   and session.expiresAt <= :now
			""")
	int expireElapsedSessions(@Param("active") StudentSessionStatus active,
			@Param("expired") StudentSessionStatus expired, @Param("reason") StudentSessionRevocationReason reason,
			@Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update StudentSessionJpaEntity session
			   set session.status = :revoked,
			       session.revokedAt = :now,
			       session.revocationReason = :reason
			 where session.studentId = :studentId
			   and session.status = :active
			""")
	int revokeActive(@Param("studentId") Long studentId, @Param("active") StudentSessionStatus active,
			@Param("revoked") StudentSessionStatus revoked, @Param("reason") StudentSessionRevocationReason reason,
			@Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update StudentSessionJpaEntity session
			   set session.status = :revoked,
			       session.revokedAt = :now,
			       session.revocationReason = :reason
			 where session.status = :active
			   and session.studentId in (
			       select student.id from StudentJpaEntity student
			        where student.organizationId = :organizationId
			   )
			""")
	int revokeActiveByOrganization(@Param("organizationId") Long organizationId,
			@Param("active") StudentSessionStatus active, @Param("revoked") StudentSessionStatus revoked,
			@Param("reason") StudentSessionRevocationReason reason, @Param("now") Instant now);

}
