package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.QuestionGovernanceSearchRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionFilterOptionsService {
    private final QuestionGovernanceSearchRepository repository;

    public QuestionFilterOptionsService(QuestionGovernanceSearchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Integer> creationYears(TenantContext tenant) {
        return repository.creationYears(tenant);
    }
}
