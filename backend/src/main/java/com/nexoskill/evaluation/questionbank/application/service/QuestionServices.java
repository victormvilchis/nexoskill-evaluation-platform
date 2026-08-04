package com.nexoskill.evaluation.questionbank.application.service;
import com.nexoskill.evaluation.questionbank.application.model.*;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.domain.PublicIdNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
public final class QuestionServices {
    private QuestionServices() {
    }

    @Service
    public static class Create {
        private final QuestionBankPort port;
        private final QuestionValidator validator;

        public Create(QuestionBankPort port, QuestionValidator validator) {
            this.port = port;
            this.validator = validator;
        }
        @Transactional
        public QuestionDetail execute(CreateQuestionCommand command) {
            var type = parseType(command.typeCode());
            validateDifficulty(command.difficultyCode());
            validator.validate(type, command.statement(), command.categoryPublicIds(), command.answerSettings(),
                    command.options(), command.codeContent());
            return port.create(command);
        }
    }
    @Service
    public static class Update {
        private final QuestionBankPort port;
        private final QuestionValidator validator;

        public Update(QuestionBankPort port, QuestionValidator validator) {
            this.port = port;
            this.validator = validator;
        }
        @Transactional
        public QuestionDetail execute(UpdateQuestionCommand command) {
            PublicIdNormalizer.requiredUuid(command.publicId(), "QUESTION_ID_INVALID",
                    "La pregunta indicada no es válida.");
            var type = parseType(command.typeCode());
            validateDifficulty(command.difficultyCode());
            validator.validate(type, command.statement(), command.categoryPublicIds(), command.answerSettings(),
                    command.options(), command.codeContent());
            return port.update(command);
        }
    }
    @Service
    public static class Get {
        private final QuestionBankPort port;
        public Get(QuestionBankPort port) { this.port = port; }
        @Transactional(readOnly = true)
        public QuestionDetail execute(String id) { return port.get(id); }
    }

    @Service
    public static class Search {
        private final QuestionBankPort port;
        public Search(QuestionBankPort port) { this.port = port; }
        @Transactional(readOnly = true)
        public QuestionPage execute(String query, String statusValue, String type, String category,
                String scopeValue, String organizationPublicId, String technologyPublicId,
                String difficultyCode, String levelCode, String creatorPublicId,
                java.time.LocalDate createdFrom, java.time.LocalDate createdTo,
                java.time.LocalDate updatedFrom, java.time.LocalDate updatedTo,
                Boolean clonedToGlobal, Boolean inUse, int page, int size) {
            QuestionStatus status = parseStatus(statusValue);
            ContentScope scope = parseScope(scopeValue);
            QuestionSearchFilter filter = new QuestionSearchFilter(query, status, type, category, scope,
                    organizationPublicId, technologyPublicId, difficultyCode, levelCode, creatorPublicId,
                    createdFrom, createdTo, updatedFrom, updatedTo, clonedToGlobal, inUse);
            return port.search(filter, Math.max(0, page), Math.min(Math.max(size, 1), 100));
        }
        public QuestionPage execute(String query, String statusValue, String type, String category,
                int page, int size) {
            return execute(query, statusValue, type, category, null, null, null,
                    null, null, null, null, null, null, null, null, null, page, size);
        }
        private QuestionStatus parseStatus(String value) {
            if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) return null;
            try {
                return QuestionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                throw new BusinessException("QUESTION_STATUS_INVALID", "El estado indicado no es válido.");
            }
        }
        private ContentScope parseScope(String value) {
            if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) return null;
            try {
                return ContentScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                throw new BusinessException("QUESTION_SCOPE_INVALID", "El alcance indicado no es válido.");
            }
        }
    }
    @Service
    public static class Duplicate {
        private final QuestionBankPort port;
        private final TenantContextResolver tenantContextResolver;
        private final HttpServletRequest request;

        public Duplicate(QuestionBankPort port,
                TenantContextResolver tenantContextResolver, HttpServletRequest request) {
            this.port = port;
            this.tenantContextResolver = tenantContextResolver;
            this.request = request;
        }

        @Transactional
        public QuestionDetail execute(String id, Long actor) {
            return execute(id, null, null, actor);
        }

        @Transactional
        public QuestionDetail execute(String id, String requestedTargetScope,
                String requestedOrganizationPublicId, Long actor) {
            QuestionDetail source = port.get(id);
            if (source == null || source.ownership() == null || source.ownership().scope() == null) {
                throw new BusinessException("QUESTION_DUPLICATE_CONTEXT_INVALID",
                        "No fue posible determinar el alcance de la pregunta que intentas duplicar.");
            }
            if (source.status() == QuestionStatus.DELETED) {
                throw new BusinessException("QUESTION_DELETED",
                        "No se puede duplicar una pregunta eliminada.");
            }
            if (actor == null) {
                throw new BusinessException("QUESTION_ACTOR_REQUIRED",
                        "No fue posible identificar al usuario que realiza la duplicación.");
            }

            var tenant = tenantContextResolver.resolve(request);
            assertCanDuplicate(source, tenant);

            if (requestedTargetScope == null || requestedTargetScope.isBlank()) {
                return port.duplicate(id, actor);
            }
            if (!tenant.globalAdministrator()) {
                throw new BusinessException("QUESTION_DUPLICATE_TARGET_FORBIDDEN",
                        "Solo el Administrador global puede seleccionar otro destino para la duplicación.");
            }
            if (source.ownership().scope() != ContentScope.GLOBAL) {
                throw new BusinessException("QUESTION_DUPLICATE_GLOBAL_SOURCE_REQUIRED",
                        "La selección de destino solo está disponible al duplicar una pregunta global.");
            }

            ContentScope targetScope;
            try {
                targetScope = ContentScope.valueOf(requestedTargetScope.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("QUESTION_DUPLICATE_TARGET_INVALID",
                        "El destino seleccionado para la duplicación no es válido.");
            }
            if (targetScope == ContentScope.GLOBAL) {
                return port.duplicate(id, actor);
            }
            if (requestedOrganizationPublicId == null || requestedOrganizationPublicId.isBlank()) {
                throw new BusinessException("QUESTION_DUPLICATE_OWNER_INVALID",
                        "Selecciona la organización propietaria de la nueva pregunta.");
            }
            return port.duplicateGlobalToOrganization(id, requestedOrganizationPublicId, actor);
        }

        private static void assertCanDuplicate(QuestionDetail source,
                com.nexoskill.evaluation.organizations.domain.model.TenantContext tenant) {
            if (tenant != null && tenant.globalAdministrator()) return;
            if (tenant == null || !tenant.hasOrganization()
                    || source.ownership().scope() != ContentScope.ORGANIZATION
                    || !Objects.equals(source.ownership().organizationPublicId(),
                            tenant.organizationPublicId())) {
                throw new BusinessException("QUESTION_DUPLICATE_FORBIDDEN",
                        "No tienes permisos para duplicar esta pregunta dentro del contexto actual.");
            }
        }
    }

    @Service
    public static class CopyToOrganization {
        private final QuestionBankPort port;
        public CopyToOrganization(QuestionBankPort port) { this.port = port; }
        @Transactional
        public QuestionDetail execute(String id, Long actor) { return port.copyToOrganization(id, actor); }
    }

    @Service
    public static class ChangeStatus {
        private final QuestionBankPort port;
        public ChangeStatus(QuestionBankPort port) { this.port = port; }
        @Transactional
        public QuestionDetail execute(String id, QuestionStatus status, long version, Long actor) {
            if (status != QuestionStatus.ACTIVE && status != QuestionStatus.ARCHIVED) {
                throw new BusinessException("QUESTION_STATUS_INVALID", "El estado solicitado no es válido.");
            }
            return port.changeStatus(id, status, version, actor);
        }
    }
    @Service
    public static class Delete {
        private final QuestionBankPort port;
        public Delete(QuestionBankPort port) { this.port = port; }
        @Transactional
        public void execute(String id, long version, String reason, Long actor) {
            String normalizedReason = reason == null || reason.isBlank()
                    ? "Eliminación física desde el Banco de preguntas."
                    : reason.trim();
            port.deletePermanently(id, version, normalizedReason, actor);
        }
    }
    @Service
    public static class Restore {
        private final QuestionBankPort port;
        public Restore(QuestionBankPort port) { this.port = port; }
        @Transactional
        public QuestionDetail execute(String id, long version, Long actor) {
            return port.restore(id, version, actor);
        }
    }
    private static void validateDifficulty(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("QUESTION_DIFFICULTY_REQUIRED", "La dificultad es obligatoria.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("JR", "STD", "SR").contains(normalized)) {
            throw new BusinessException("QUESTION_DIFFICULTY_INVALID", "La dificultad debe ser JR, STD o SR.");
        }
    }
    private static com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode parseType(String value) {
        try {
            return com.nexoskill.evaluation.questionbank.domain.model.QuestionTypeCode
                    .valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new BusinessException("QUESTION_TYPE_INVALID", "El tipo de pregunta no es válido.");
        }
    }
}
