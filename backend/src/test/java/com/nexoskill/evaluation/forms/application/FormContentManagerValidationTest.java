package com.nexoskill.evaluation.forms.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.nexoskill.evaluation.forms.domain.FormContentMode;
import com.nexoskill.evaluation.forms.infrastructure.FormSectionRepository;
import com.nexoskill.evaluation.globalcontent.application.service.GlobalContentAccessPolicy;
import com.nexoskill.evaluation.organizations.domain.model.ContentScope;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionCategoryRepository;
import com.nexoskill.evaluation.questionbank.infrastructure.persistence.SpringDataQuestionRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FormContentManagerValidationTest {
    private final FormContentManager manager = new FormContentManager(
            mock(FormSectionRepository.class),
            mock(SpringDataQuestionRepository.class),
            mock(SpringDataQuestionCategoryRepository.class),
            mock(OrganizationRepository.class),
            mock(GlobalContentAccessPolicy.class));
    private final FormCreationTargetResolver.Target target = new FormCreationTargetResolver.Target(
            ContentScope.GLOBAL, 1L, "global-id", "GLOBAL", "GLOBAL");

    @Test
    void manualModeRequiresQuestionsAndRejectsPools() {
        assertThatThrownBy(() -> manager.validate(FormContentMode.MANUAL, List.of(), List.of(), target, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos una pregunta");

        var question = new FormModels.QuestionItem("11111111-1111-1111-1111-111111111111", 1,
                BigDecimal.ONE, true);
        var pool = new FormModels.PoolItem(null, "CATEGORY",
                "22222222-2222-2222-2222-222222222222", 1, null, 1);
        assertThatThrownBy(() -> manager.validate(FormContentMode.MANUAL,
                List.of(question), List.of(pool), target, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pool aleatorio");
    }

    @Test
    void randomPoolRejectsManualQuestionsAndRequiresCategories() {
        var question = new FormModels.QuestionItem("11111111-1111-1111-1111-111111111111", 1,
                BigDecimal.ONE, true);
        assertThatThrownBy(() -> manager.validate(FormContentMode.RANDOM_POOL,
                List.of(question), List.of(), target, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("preguntas manuales");

        assertThatThrownBy(() -> manager.validate(FormContentMode.RANDOM_POOL,
                List.of(), List.of(), target, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("categoría");
    }
}
