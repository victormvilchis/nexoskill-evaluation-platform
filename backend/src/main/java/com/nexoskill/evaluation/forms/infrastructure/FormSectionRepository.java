package com.nexoskill.evaluation.forms.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormSectionRepository extends JpaRepository<FormSectionJpaEntity, Long> {
	List<FormSectionJpaEntity> findAllByFormIdOrderBySectionOrderAsc(Long formId);
}
