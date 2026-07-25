package com.nexoskill.evaluation.questionbank.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.questionbank.application.model.CreateQuestionCommand;
import com.nexoskill.evaluation.questionbank.application.model.QuestionDetail;
import com.nexoskill.evaluation.questionbank.application.model.QuestionOptionCommand;
import com.nexoskill.evaluation.questionbank.application.port.out.QuestionBankPort;
import com.nexoskill.evaluation.questionbank.domain.model.QuestionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateQuestionServiceTest {

    @Test
    void keepsCategoryUuidCanonicalInsteadOfUppercasingIt() {
        QuestionBankPort port = mock(QuestionBankPort.class);
        AuditLogPort audit = mock(AuditLogPort.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T12:00:00Z"),
                ZoneOffset.UTC
        );
        when(port.create(any())).thenReturn(new QuestionDetail(
                "9a6ad962-20a8-4303-b98e-770997f48db8",
                "Pregunta",
                null,
                "SINGLE_CHOICE",
                "Opción única",
                "BASIC",
                "Básico",
                "7a6ad962-20a8-4303-b98e-770997f48db8",
                "Java",
                QuestionStatus.DRAFT,
                1,
                0,
                null,
                List.of(),
                clock.instant(),
                null
        ));

        CreateQuestionService service = new CreateQuestionService(
                port,
                new QuestionDraftValidator(),
                audit,
                clock
        );
        service.create(new CreateQuestionCommand(
                "SINGLE_CHOICE",
                "BASIC",
                "7A6AD962-20A8-4303-B98E-770997F48DB8",
                "Pregunta",
                null,
                List.of(
                        new QuestionOptionCommand("A", true),
                        new QuestionOptionCommand("B", false)
                ),
                1L,
                "127.0.0.1",
                "JUnit"
        ));

        ArgumentCaptor<QuestionBankPort.NewQuestionData> captor =
                ArgumentCaptor.forClass(QuestionBankPort.NewQuestionData.class);
        verify(port).create(captor.capture());
        assertThat(captor.getValue().categoryPublicId())
                .isEqualTo("7a6ad962-20a8-4303-b98e-770997f48db8");
    }
}
