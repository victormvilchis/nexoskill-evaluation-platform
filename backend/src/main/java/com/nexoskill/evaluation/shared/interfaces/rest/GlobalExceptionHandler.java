package com.nexoskill.evaluation.shared.interfaces.rest;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.nexoskill.evaluation.authentication.domain.AuthenticationException;
import com.nexoskill.evaluation.authentication.infrastructure.security.PlatformAccessDeniedHandler;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final PlatformAccessDeniedHandler platformAccessDeniedHandler;

    /** Conservado para pruebas unitarias aisladas. */
    public GlobalExceptionHandler() {
        this.platformAccessDeniedHandler = null;
    }

    @Autowired
    public GlobalExceptionHandler(PlatformAccessDeniedHandler platformAccessDeniedHandler) {
        this.platformAccessDeniedHandler = platformAccessDeniedHandler;
    }

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception,
			HttpServletRequest request) {

		HttpStatus status = switch (exception.getCode()) {
		case "ACCOUNT_INACTIVE", "ACCOUNT_SUSPENDED", "ACCOUNT_TEMPORARILY_LOCKED",
				"ORGANIZATION_INACTIVE", "ORGANIZATION_EXPIRED", "ACCESS_EXPIRED" -> HttpStatus.FORBIDDEN;
		case "AUTHENTICATION_FAILED", "TEMP_PASSWORD_EXPIRED", "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
		default -> HttpStatus.UNAUTHORIZED;
		};

		return response(status, exception.getCode(), exception.getMessage(), request.getRequestURI(), null);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {

		HttpStatus status = switch (exception.getCode()) {
		case "QUESTION_CONCURRENT_MODIFICATION", "USER_CONCURRENT_MODIFICATION", "DATA_CONFLICT",
                "CATEGORY_CONCURRENT_MODIFICATION", "COLLECTION_CONCURRENT_MODIFICATION", "CATEGORY_ALREADY_EXISTS",
                "COLLECTION_ALREADY_EXISTS", "CATEGORY_CODE_ALREADY_EXISTS", "CATEGORY_IN_USE",
                "CATEGORY_MUST_BE_INACTIVE", "CATEGORY_STATUS_TRANSITION_INVALID", "CATEGORY_DELETED",
                "CATEGORY_INACTIVE", "GLOBAL_ORGANIZATION_LICENSE_INVALID", "GLOBAL_ORGANIZATION_RESERVED",
                "ORGANIZATION_CODE_EXISTS", "ORGANIZATION_CONCURRENT_MODIFICATION", "GLOBAL_ORGANIZATION_IMMUTABLE",
                "STUDENT_EMAIL_EXISTS", "STUDENT_CODE_EXISTS", "STUDENT_CONFLICT", "STUDENT_VERSION_CONFLICT",
                "STUDENT_SESSION_ALREADY_ACTIVE", "STUDENT_STATUS_TRANSITION_INVALID", "STUDENT_STATUS_UNCHANGED",
                "USER_EMAIL_EXISTS", "USER_STATUS_TRANSITION_INVALID",
                "USER_NOT_DELETED", "USER_DELETED", "GLOBAL_CONTENT_ALREADY_PROMOTED",
                "GLOBAL_CONTENT_DEPENDENCIES_MISSING", "GLOBAL_CONTENT_DEPENDENCY_CYCLE",
                "GLOBAL_CONTENT_EDITORIAL_TRANSITION_INVALID", "GLOBAL_CONTENT_NOT_PUBLISHED",
                "GLOBAL_CONTENT_CUSTOMIZED_COPY", "GLOBAL_CONTENT_GRANT_ALREADY_EXISTS",
                "ORGANIZATION_DUPLICATE", "ORGANIZATION_TRANSITION_INVALID", "ORGANIZATION_MUST_BE_INACTIVE",
                "ORGANIZATION_STATUS_UNCHANGED", "ORGANIZATION_RESTORE_INVALID", "ORGANIZATION_RESTORE_REQUIRED",
                "QUESTION_GLOBAL_CLONE_EXISTS", "QUESTION_DUPLICATE_SCOPE_INVALID",
                "QUESTION_DUPLICATE_CONFIGURATION_INVALID", "STUDENT_DELETION_IN_PROGRESS",
                "CERTIFICATION_CYCLE_CONFLICT", "CERTIFICATION_CYCLE_VERSION_CONFLICT",
                "CERTIFICATION_ATTEMPT_VERSION_CONFLICT", "STUDENT_IMPORT_ALREADY_APPLIED",
                "STUDENT_IMPORT_APPLY_IN_PROGRESS" -> HttpStatus.CONFLICT;
		case "QUESTION_NOT_FOUND", "CATEGORY_NOT_FOUND", "COLLECTION_NOT_FOUND", "USER_NOT_FOUND",
                "ORGANIZATION_NOT_FOUND", "GLOBAL_ORGANIZATION_NOT_FOUND", "ORGANIZATION_LICENSE_NOT_FOUND",
                "STUDENT_NOT_FOUND", "STUDENT_SESSION_NOT_FOUND", "GLOBAL_CONTENT_NOT_FOUND",
                "GLOBAL_CONTENT_PROMOTION_NOT_FOUND", "GLOBAL_CONTENT_VERSION_NOT_FOUND",
                "GLOBAL_CONTENT_GRANT_NOT_FOUND", "GLOBAL_CONTENT_DISTRIBUTION_NOT_FOUND",
                "GLOBAL_CONTENT_REPLICATION_LINK_NOT_FOUND", "QUESTION_TECHNOLOGY_NOT_FOUND",
                "QUESTION_DUPLICATE_SOURCE_NOT_FOUND", "QUESTION_MEDIA_NOT_FOUND",
                "CERTIFICATION_CYCLE_NOT_FOUND", "CERTIFICATION_ATTEMPT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
		case "STUDENT_INVALID_CREDENTIALS", "STUDENT_ACCOUNT_UNAVAILABLE", "STUDENT_ACCESS_EXPIRED",
                "STUDENT_TEMP_PASSWORD_EXPIRED" -> HttpStatus.UNAUTHORIZED;
		case "SELF_DEACTIVATE_NOT_ALLOWED", "SELF_DELETE_NOT_ALLOWED", "SELF_SUSPEND_NOT_ALLOWED",
                "SELF_ROLE_CHANGE_NOT_ALLOWED", "SELF_ORGANIZATION_CHANGE_NOT_ALLOWED",
                "LAST_ADMINISTRATOR_REQUIRED", "CATEGORY_GLOBAL_IMMUTABLE", "CATEGORY_GLOBAL_CONTEXT_REQUIRED",
                "CATEGORY_TENANT_INVALID", "TENANT_NOT_RESOLVED", "ORGANIZATION_CONTEXT_REQUIRED",
                "ADMIN_GLOBAL_ORGANIZATION_REQUIRED", "CUSTOMER_ORGANIZATION_REQUIRED",
                "ORGANIZATION_NOT_OPERATIONAL", "GLOBAL_CONTENT_SCOPE_REQUIRED",
                "GLOBAL_CONTENT_ORGANIZATIONAL_SOURCE_REQUIRED", "GLOBAL_CONTENT_CUSTOMER_REQUIRED",
                "GLOBAL_CONTENT_DEPENDENCY_SCOPE_INVALID", "QUESTION_GLOBAL_CLONE_FORBIDDEN",
                "QUESTION_ACCESS_FORBIDDEN", "QUESTION_DUPLICATE_FORBIDDEN",
                "QUESTION_GLOBAL_MANAGEMENT_FORBIDDEN", "QUESTION_OPERATION_FORBIDDEN",
                "STUDENT_DELETE_FORBIDDEN", "STUDENT_ORGANIZATION_FORBIDDEN",
                "STUDENT_GLOBAL_FORBIDDEN", "CERTIFICATION_ACCESS_FORBIDDEN",
                "CERTIFICATION_OPERATION_FORBIDDEN", "CERTIFICATIONS_NOT_ENABLED" -> HttpStatus.FORBIDDEN;
        case "ORGANIZATION_PERSISTENCE_INVALID", "ORGANIZATION_LICENSE_PERSISTENCE_INVALID",
                "ORGANIZATION_CREATE_FAILED", "QUESTION_GLOBAL_CLONE_FAILED",
                "STUDENT_PERMANENT_DELETE_FAILED", "QUESTION_DUPLICATE_FAILED",
                "QUESTION_MEDIA_STORAGE_ERROR", "QUESTION_MEDIA_READ_ERROR",
                "QUESTION_MEDIA_DELETE_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
		default -> HttpStatus.BAD_REQUEST;
		};

		return response(status, exception.getCode(), exception.getMessage(), request.getRequestURI(),
                exception.getFieldErrors().isEmpty() ? null : exception.getFieldErrors());
	}

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        LOGGER.warn("Invalid request payload on {} ({})", request.getRequestURI(),
                diagnosticType(exception));
        String field = "request";
        Throwable cause = exception.getCause();
        if (cause instanceof JsonMappingException mappingException && !mappingException.getPath().isEmpty()) {
            field = mappingException.getPath().stream()
                    .map(reference -> reference.getFieldName() != null
                            ? reference.getFieldName()
                            : "[" + reference.getIndex() + "]")
                    .collect(Collectors.joining("."));
        }
        String fieldMessage = switch (field) {
            case "validFrom", "expiresAt" -> "La fecha enviada no tiene un formato válido.";
            case "status" -> "El estado enviado no es válido.";
            default -> "El valor enviado no tiene un formato válido.";
        };
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene un formato inválido.", request.getRequestURI(),
                Map.of(field, fieldMessage));
    }

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception,
			HttpServletRequest request) {
		LOGGER.warn("Access denied on {} ({})", request.getRequestURI(),
                diagnosticType(exception));
        if (platformAccessDeniedHandler != null) {
            PlatformAccessDeniedHandler.Denial denial = platformAccessDeniedHandler.resolveAndAudit(request);
            return response(HttpStatus.FORBIDDEN, denial.code(), denial.message(), request.getRequestURI(), null);
        }
		return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
				exception.getMessage() == null || exception.getMessage().isBlank()
						? "No tienes permiso para completar esta operación."
						: exception.getMessage(),
				request.getRequestURI(), null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
			HttpServletRequest request) {

		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : exception.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}

		return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La solicitud contiene datos inválidos.",
				request.getRequestURI(), fields);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ApiErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException exception,
			HttpServletRequest request) {

		LOGGER.warn("Concurrent modification on {}", request.getRequestURI(), exception);
		return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
				"La información fue modificada por otra sesión. Actualiza la página e intenta nuevamente.",
				request.getRequestURI(), null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception,
			HttpServletRequest request) {

		LOGGER.warn("Data integrity violation on {}", request.getRequestURI(), exception);
		return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
				"No fue posible guardar porque los datos entran en conflicto con información existente.",
				request.getRequestURI(), null);
	}

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        LOGGER.warn("Upload too large on {}", request.getRequestURI());
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "STUDENT_IMPORT_FILE_TOO_LARGE",
                "El archivo supera el límite de 50 MB permitido para la importación.",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException exception,
            HttpServletRequest request) {
        LOGGER.warn("Invalid multipart request on {} ({})", request.getRequestURI(),
                diagnosticType(exception));
        return response(HttpStatus.BAD_REQUEST, "STUDENT_IMPORT_FILE_INVALID",
                "No fue posible leer el archivo enviado. Verifica que sea un .xlsx válido e inténtalo nuevamente.",
                request.getRequestURI(), null);
    }

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {

		LOGGER.error("Unexpected error on {}", request.getRequestURI(), exception);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				"Ocurrió un error interno. Intenta nuevamente.", request.getRequestURI(), null);
	}

    private String diagnosticType(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, String path,
			Map<String, String> fieldErrors) {

		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(Instant.now(), status.value(), code, message, path, fieldErrors));
	}
}
