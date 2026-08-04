package com.nexoskill.evaluation.globalcontent.application.service;

import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.GrantCommand;
import com.nexoskill.evaluation.globalcontent.application.service.OrganizationContentGrantService.GrantOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentDistributionWorker {
	private final OrganizationContentGrantService grants;

	public ContentDistributionWorker(OrganizationContentGrantService grants) {
		this.grants = grants;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public GrantOutcome process(GrantCommand command, Long actorUserId) {
		return grants.grantInternal(command, actorUserId);
	}
}
