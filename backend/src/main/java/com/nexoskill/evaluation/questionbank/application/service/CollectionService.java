package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionCollectionPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.application.PaginationParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionService {
	private final QuestionCollectionPort port;

	public CollectionService(QuestionCollectionPort p) {
		port = p;
	}

	@Transactional
	public CollectionDetail create(CollectionCommands.Create c) {
		validate(c.name(), c.categoryPublicIds(), c.questionPublicIds());
		return port.create(c);
	}

	@Transactional
	public CollectionDetail update(CollectionCommands.Update c) {
		validate(c.name(), c.categoryPublicIds(), c.questionPublicIds());
		return port.update(c);
	}

	@Transactional(readOnly = true)
	public CollectionDetail get(String id) {
		return port.get(id);
	}

	@Transactional(readOnly = true)
	public CollectionPage search(String q, String s, int p, int z) {
		String status = null;
		if (s != null && !s.isBlank() && !"ALL".equalsIgnoreCase(s.trim())) {
			try {
				status = com.nexoskill.evaluation.questionbank.domain.model.CollectionStatus
						.valueOf(s.trim().toUpperCase()).name();
			} catch (Exception e) {
				throw new BusinessException("COLLECTION_STATUS_INVALID", "El estado de colección no es válido.");
			}
		}
		PaginationParameters.validate(p, z);
		return port.search(q, status, p, z);
	}

	@Transactional
	public CollectionDetail status(CollectionCommands.ChangeStatus c) {
		return port.changeStatus(c);
	}

	private void validate(String name, java.util.List<String> categories, java.util.List<String> questions) {
		if (name == null || name.isBlank())
			throw new BusinessException("COLLECTION_NAME_REQUIRED", "El nombre de la colección es obligatorio.");
		if (name.trim().length() > 180)
			throw new BusinessException("COLLECTION_NAME_TOO_LONG", "El nombre no puede superar 180 caracteres.");
		if ((categories == null || categories.isEmpty()) && (questions == null || questions.isEmpty()))
			throw new BusinessException("COLLECTION_EMPTY",
					"La colección debe contener al menos una categoría o pregunta.");
		if (new java.util.HashSet<>(categories).size() != categories.size())
			throw new BusinessException("COLLECTION_CATEGORY_DUPLICATED",
					"No repitas categorías dentro de la colección.");
		if (new java.util.HashSet<>(questions).size() != questions.size())
			throw new BusinessException("COLLECTION_QUESTION_DUPLICATED",
					"No repitas preguntas específicas dentro de la colección.");
	}
}
