package com.nexoskill.evaluation.users.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPermissionJpaRepository extends JpaRepository<PermissionJpaEntity, Long> {

    List<PermissionJpaEntity> findAllByOrderByModuleCodeAscNameAsc();

    List<PermissionJpaEntity> findByCodeIn(Collection<String> codes);
}
