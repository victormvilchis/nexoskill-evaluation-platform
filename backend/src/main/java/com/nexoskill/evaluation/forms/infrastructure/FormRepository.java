package com.nexoskill.evaluation.forms.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FormRepository extends JpaRepository<FormJpaEntity,Long> {
 Optional<FormJpaEntity> findByPublicId(String publicId);
 boolean existsByCode(String code);
}
