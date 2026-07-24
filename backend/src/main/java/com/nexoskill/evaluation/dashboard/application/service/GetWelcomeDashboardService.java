package com.nexoskill.evaluation.dashboard.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.dashboard.application.model.DashboardModule;
import com.nexoskill.evaluation.dashboard.application.model.WelcomeDashboard;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GetWelcomeDashboardService {

    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public GetWelcomeDashboardService(
            AuditLogPort auditLogPort,
            Clock clock) {
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    public WelcomeDashboard get(
            AuthenticatedUser user,
            String ipAddress,
            String userAgent) {

        Instant now = clock.instant();
        boolean administrator = user.roles().contains("ADMINISTRATOR");

        auditLogPort.record(
                user.internalId(),
                "DASHBOARD_ACCESSED",
                "DASHBOARD",
                "El usuario consultó su panel de bienvenida.",
                ipAddress,
                userAgent,
                Map.of("panelType", administrator ? "ADMIN" : "USER"),
                now
        );

        return new WelcomeDashboard(
                administrator ? "ADMIN" : "USER",
                "Bienvenido, " + user.displayName(),
                administrator
                        ? "La base administrativa está lista para incorporar "
                          + "usuarios, preguntas y evaluaciones."
                        : "Tu cuenta está activa. Próximamente podrás consultar "
                          + "y realizar las evaluaciones asignadas.",
                now,
                administrator
                        ? adminModules()
                        : userModules()
        );
    }

    private List<DashboardModule> adminModules() {
        return List.of(
                new DashboardModule(
                        "USERS",
                        "Usuarios",
                        "Administración de usuarios y vigencias.",
                        false
                ),
                new DashboardModule(
                        "QUESTIONS",
                        "Banco de preguntas",
                        "Creación y clasificación de preguntas.",
                        false
                ),
                new DashboardModule(
                        "EXAMS",
                        "Evaluaciones",
                        "Configuración, asignación y habilitación.",
                        false
                ),
                new DashboardModule(
                        "RESULTS",
                        "Resultados",
                        "Consulta de intentos y calificaciones.",
                        false
                )
        );
    }

    private List<DashboardModule> userModules() {
        return List.of(
                new DashboardModule(
                        "MY_EXAMS",
                        "Mis evaluaciones",
                        "Evaluaciones disponibles y próximas.",
                        false
                ),
                new DashboardModule(
                        "MY_RESULTS",
                        "Mis resultados",
                        "Historial de calificaciones.",
                        false
                ),
                new DashboardModule(
                        "PROFILE",
                        "Mi perfil",
                        "Información y seguridad de la cuenta.",
                        false
                )
        );
    }
}
