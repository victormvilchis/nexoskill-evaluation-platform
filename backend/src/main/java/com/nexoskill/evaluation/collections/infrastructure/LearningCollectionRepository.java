package com.nexoskill.evaluation.collections.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface LearningCollectionRepository extends JpaRepository<LearningCollectionJpaEntity, Long> {

	Optional<LearningCollectionJpaEntity> findByPublicId(String publicId);

	boolean existsByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select collection from LearningCollectionJpaEntity collection where collection.publicId = :publicId")
	Optional<LearningCollectionJpaEntity> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
