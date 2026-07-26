package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UserOrganizationMembershipRepository extends Repository<OrganizationJpaEntity, Long> {
	@Query(value = """
			select o.* from ORGANIZATION o
			join APP_USER_ORGANIZATION uo on uo.ORGANIZATION_ID = o.ORGANIZATION_ID
			where uo.USER_ID = :userId and uo.STATUS = 'ACTIVE'
			""", nativeQuery = true)
	Optional<OrganizationJpaEntity> findActiveOrganizationForUser(@Param("userId") Long userId);
}
