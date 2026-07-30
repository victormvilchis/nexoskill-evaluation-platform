package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class PlatformAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformAccessDeniedHandler.class);
    private static final Set<String> READ_ONLY_MODULE_PREFIXES = Set.of(
            "/api/v1/admin/catalogs",
            "/api/v1/admin/questions",
            "/api/v1/admin/forms",
            "/api/v1/admin/collections");

    private final AuditLogPort audit;
    private final Clock clock;

    public PlatformAccessDeniedHandler(AuditLogPort audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Denial denial = resolveAndAudit(request);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + denial.code()
                + "\",\"message\":\"" + denial.message() + "\"}");
    }

    /**
     * Resuelve el mensaje funcional y registra el intento. También es utilizado por
     * el manejador MVC porque las denegaciones de seguridad a nivel de método pueden
     * ser resueltas por DispatcherServlet antes de regresar al filtro de seguridad.
     */
    public Denial resolveAndAudit(HttpServletRequest request) {
        AuthenticatedUser user = authenticatedUser();
        boolean certificationOperationDenied = user != null
                && user.roles().contains("ADMINISTRATOR")
                && isCertificationOperation(request.getRequestURI());
        boolean managerWriteDenied = user != null
                && user.roles().contains("MANAGER")
                && isWrite(request.getMethod())
                && READ_ONLY_MODULE_PREFIXES.stream().anyMatch(request.getRequestURI()::startsWith);

        Denial denial = certificationOperationDenied
                ? new Denial("CERTIFICATION_OPERATION_FORBIDDEN",
                        "La gestión operativa de certificaciones corresponde únicamente a Gestores y Supervisores de la organización.")
                : managerWriteDenied
                    ? new Denial("ORGANIZATIONAL_MODULE_READ_ONLY",
                            "Tu rol tiene acceso de consulta a este módulo, pero no permite realizar modificaciones.")
                    : new Denial("ACCESS_DENIED", "No tienes permisos para esta operación.");
        auditDeniedAttempt(user, request, denial.code());
        return denial;
    }

    private AuthenticatedUser authenticatedUser() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user : null;
    }


    private boolean isCertificationOperation(String path) {
        return path != null && (path.startsWith("/api/v1/admin/certification-")
                || (path.startsWith("/api/v1/admin/students/") && path.contains("/certifications")));
    }

    private boolean isWrite(String method) {
        return !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method);
    }

    private void auditDeniedAttempt(AuthenticatedUser user, HttpServletRequest request, String code) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("method", request.getMethod());
            values.put("path", request.getRequestURI());
            values.put("roles", user == null ? Set.of() : user.roles());
            values.put("reason", code);
            audit.record(user == null ? null : user.internalId(), "AUTHORIZATION_DENIED", "SECURITY",
                    "Se rechazó una operación sin permisos efectivos.", request.getRemoteAddr(),
                    request.getHeader("User-Agent"), values, clock.instant());
        } catch (RuntimeException exception) {
            LOGGER.warn("No fue posible auditar la denegación de acceso a {} {}.",
                    request.getMethod(), request.getRequestURI(), exception);
        }
    }

    public record Denial(String code, String message) {}
}
