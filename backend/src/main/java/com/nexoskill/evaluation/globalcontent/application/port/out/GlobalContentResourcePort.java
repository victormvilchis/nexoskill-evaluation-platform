package com.nexoskill.evaluation.globalcontent.application.port.out;

import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ContentResource;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.Dependency;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ReviewFilter;
import com.nexoskill.evaluation.globalcontent.application.model.GlobalContentModels.ReviewPage;
import com.nexoskill.evaluation.globalcontent.domain.model.GlobalContentType;
import java.util.List;
import java.util.Map;

public interface GlobalContentResourcePort {
	record ResourceKey(GlobalContentType type, Long internalId) {
	}

	ReviewPage review(ReviewFilter filter);

	ContentResource find(GlobalContentType type, String publicId);

	ContentResource findByInternalId(GlobalContentType type, Long internalId);

	List<Dependency> dependencies(GlobalContentType type, Long internalId);

	List<ContentResource> possibleGlobalDuplicates(ContentResource source);

	ContentResource copyToGlobal(GlobalContentType type, Long sourceInternalId,
			Map<ResourceKey, Long> dependencyTargets, Long actorUserId);

	ContentResource copyToOrganization(GlobalContentType type, Long globalInternalId, Long organizationId,
			long sourceGlobalVersion, Map<ResourceKey, Long> dependencyTargets, Long actorUserId, boolean editable);

	void markCustomized(GlobalContentType type, Long targetInternalId);

	void changeOperationalStatus(GlobalContentType type, Long internalId, String status, Long actorUserId);
}
