package com.nexoskill.evaluation.questionbank.application.service;

import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.UpdateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionAvailabilityMode;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda la pregunta y, durante la creación GLOBAL, genera copias organizacionales
 * independientes dentro de la misma transacción. La actualización conserva el
 * flujo legado de disponibilidad únicamente cuando el cliente lo solicita.
 */
@Service
public class QuestionSaveService {
    private final QuestionServices.Create create;
    private final QuestionServices.Update update;
    private final QuestionAvailabilityService availability;
    private final QuestionBankPort questions;

    public QuestionSaveService(QuestionServices.Create create, QuestionServices.Update update,
            QuestionAvailabilityService availability, QuestionBankPort questions) {
        this.create = create;
        this.update = update;
        this.availability = availability;
        this.questions = questions;
    }

    @Transactional
    public QuestionDetail create(CreateQuestionCommand command, QuestionAvailabilityMode availabilityMode,
            List<String> organizationPublicIds, TenantContext tenant, Actor actor) {
        QuestionDetail result = create.execute(command);
        distributeNewGlobalQuestion(result, availabilityMode, organizationPublicIds, tenant, actor);
        return result;
    }

    @Transactional
    public QuestionDetail update(UpdateQuestionCommand command, QuestionAvailabilityMode availabilityMode,
            List<String> organizationPublicIds, TenantContext tenant, Actor actor) {
        QuestionDetail result = update.execute(command);
        updateAvailability(result, availabilityMode, organizationPublicIds, tenant, actor);
        return result;
    }


    private void distributeNewGlobalQuestion(QuestionDetail question, QuestionAvailabilityMode mode,
            List<String> organizationPublicIds, TenantContext tenant, Actor actor) {
        if (mode == null || mode == QuestionAvailabilityMode.NONE) return;
        assertGlobalDistributionContext(question, tenant, actor);

        Set<String> targets = new LinkedHashSet<>();
        if (mode == QuestionAvailabilityMode.GLOBAL) {
            targets.addAll(questions.activeCommercialOrganizationPublicIds());
        } else if (mode == QuestionAvailabilityMode.SELECTED_ORGANIZATIONS) {
            if (organizationPublicIds != null) {
                organizationPublicIds.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .forEach(targets::add);
            }
            if (targets.isEmpty()) {
                throw new BusinessException("QUESTION_ORGANIZATIONS_REQUIRED",
                        "Selecciona al menos una organización para distribuir la pregunta.");
            }
        } else {
            throw new BusinessException("QUESTION_AVAILABILITY_REQUIRED",
                    "Selecciona una configuración de distribución válida.");
        }

        for (String organizationPublicId : targets) {
            questions.copyGlobalToOrganization(question.publicId(), organizationPublicId, actor.userId());
        }
    }

    private void assertGlobalDistributionContext(QuestionDetail question, TenantContext tenant, Actor actor) {
        if (question == null || question.ownership() == null
                || question.ownership().scope() != ContentScope.GLOBAL) {
            throw new BusinessException("QUESTION_AVAILABILITY_SCOPE_MISMATCH",
                    "La distribución organizacional solo puede realizarse sobre una pregunta GLOBAL.");
        }
        if (tenant == null || !tenant.globalAdministrator()) {
            throw new BusinessException("QUESTION_AVAILABILITY_FORBIDDEN",
                    "Solo el Administrador global puede distribuir preguntas hacia organizaciones.");
        }
        if (actor == null || actor.userId() == null) {
            throw new BusinessException("QUESTION_ACTOR_REQUIRED",
                    "No fue posible identificar al usuario que realiza la operación.");
        }
    }

    private void updateAvailability(QuestionDetail question, QuestionAvailabilityMode mode,
            List<String> organizationPublicIds, TenantContext tenant, Actor actor) {
        if (mode == null) return;
        if (question == null || question.ownership() == null || question.ownership().scope() == null) {
            throw new BusinessException("QUESTION_CONTEXT_INVALID",
                    "No fue posible resolver el alcance autorizado de la pregunta.");
        }
        if (question.ownership().scope() != ContentScope.GLOBAL) {
            throw new BusinessException("QUESTION_AVAILABILITY_SCOPE_MISMATCH",
                    "La disponibilidad organizacional solo puede modificarse sobre una pregunta GLOBAL.");
        }
        if (tenant == null || !tenant.globalAdministrator()) {
            throw new BusinessException("QUESTION_AVAILABILITY_FORBIDDEN",
                    "La disponibilidad organizacional solo puede modificarse por un Administrador global sobre una pregunta global.");
        }
        if (actor == null || actor.userId() == null) {
            throw new BusinessException("QUESTION_ACTOR_REQUIRED",
                    "No fue posible identificar al usuario que realiza la operación.");
        }
        availability.update(question.publicId(),
                new QuestionAvailabilityService.UpdateCommand(mode, organizationPublicIds), tenant,
                new QuestionAvailabilityService.Actor(actor.userId(), actor.ipAddress(), actor.userAgent()));
    }

    public record Actor(Long userId, String ipAddress, String userAgent) {}
}
