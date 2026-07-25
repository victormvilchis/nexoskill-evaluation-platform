package com.nexoskill.evaluation.questionbank.infrastructure.persistence;

import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionMediaPort;
import com.nexoskill.evaluation.shared.domain.*;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class OracleQuestionMediaAdapter implements QuestionMediaPort {
	private final SpringDataQuestionMediaRepository repo;
	private final Clock clock;

	public OracleQuestionMediaAdapter(SpringDataQuestionMediaRepository r, Clock c) {
		repo = r;
		clock = c;
	}

	public QuestionMediaView save(String id, String key, String name, String type, long size, String checksum,
			Long actor) {
		return view(repo.saveAndFlush(
				QuestionMediaJpaEntity.create(id, key, name, type, size, checksum, actor, clock.instant())));
	}

	public MediaData get(String id) {
		String p = PublicIdNormalizer.requiredUuid(id, "QUESTION_MEDIA_INVALID", "La imagen indicada no es válida.");
		var e = repo.findByPublicId(p).orElseThrow(
				() -> new BusinessException("QUESTION_MEDIA_NOT_FOUND", "La imagen solicitada no existe."));
		return new MediaData(view(e), e.getStorageKey());
	}

	private QuestionMediaView view(QuestionMediaJpaEntity e) {
		return new QuestionMediaView(e.getPublicId(), e.getOriginalName(), e.getContentType(), e.getSize(),
				"/api/v1/question-media/" + e.getPublicId());
	}
}
