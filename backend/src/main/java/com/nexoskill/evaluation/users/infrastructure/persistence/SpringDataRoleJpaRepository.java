package com.nexoskill.evaluation.users.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {

	Optional<RoleJpaEntity> findByCode(String code);

	List<RoleJpaEntity> findByStatusOrderByNameAsc(String status);
}
