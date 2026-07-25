package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public final class QuestionServices {
	private QuestionServices() {
	}

	@Service
	public static class Create {
		private final QuestionBankPort port;
		private final QuestionValidator validator;

		public Create(QuestionBankPort p, QuestionValidator v) {
			port = p;
			validator = v;
		}

		@Transactional
		public QuestionDetail execute(CreateQuestionCommand c) {
			var type = parseType(c.typeCode());
			validator.validate(type, c.statement(), c.categoryPublicIds(), c.answerSettings(), c.options());
			return port.create(c);
		}
	}

	@Service
	public static class Update {
		private final QuestionBankPort port;
		private final QuestionValidator validator;

		public Update(QuestionBankPort p, QuestionValidator v) {
			port = p;
			validator = v;
		}

		@Transactional
		public QuestionDetail execute(UpdateQuestionCommand c) {
			PublicIdNormalizer.requiredUuid(c.publicId(), "QUESTION_ID_INVALID", "La pregunta indicada no es válida.");
			var type = parseType(c.typeCode());
			validator.validate(type, c.statement(), c.categoryPublicIds(), c.answerSettings(), c.options());
			return port.update(c);
		}
	}

	@Service
	public static class Get {
		private final QuestionBankPort port;

		public Get(QuestionBankPort p) {
			port = p;
		}

		@Transactional(readOnly = true)
		public QuestionDetail execute(String id) {
			return port.get(id);
		}
	}

	@Service
	public static class Search {
		private final QuestionBankPort port;

		public Search(QuestionBankPort p) {
			port = p;
		}

		@Transactional(readOnly = true)
		public QuestionPage execute(String q, String s, String t, String d, String c, int page, int size) {
			QuestionStatus status = null;
			if (s != null && !s.isBlank())
				try {
					status = QuestionStatus.valueOf(s.toUpperCase(Locale.ROOT));
				} catch (Exception e) {
					throw new BusinessException("QUESTION_STATUS_INVALID", "El estado indicado no es válido.");
				}
			return port.search(q, status, t, d, c, Math.max(0, page), Math.min(Math.max(size, 1), 100));
		}
	}

	@Service
	public static class Duplicate {
		private final QuestionBankPort port;

		public Duplicate(QuestionBankPort p) {
			port = p;
		}

		@Transactional
		public QuestionDetail execute(String id, Long actor) {
			return port.duplicate(id, actor);
		}
	}

	@Service
	public static class ChangeStatus {
		private final QuestionBankPort port;

		public ChangeStatus(QuestionBankPort p) {
			port = p;
		}

		@Transactional
		public QuestionDetail execute(String id, QuestionStatus status, long version, Long actor) {
			if (status != QuestionStatus.ACTIVE && status != QuestionStatus.ARCHIVED)
				throw new BusinessException("QUESTION_STATUS_INVALID", "El estado solicitado no es válido.");
			return port.changeStatus(id, status, version, actor);
		}
	}

	private static com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode parseType(String value) {
		try {
			return com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode
					.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (Exception e) {
			throw new BusinessException("QUESTION_TYPE_INVALID", "El tipo de pregunta no es válido.");
		}
	}
}
