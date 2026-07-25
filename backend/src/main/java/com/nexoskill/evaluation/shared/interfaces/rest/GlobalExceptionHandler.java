package com.nexoskill.evaluation.shared.interfaces.rest;

import com.nexoskill.evaluation.authentication.domain.AuthenticationException;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception,
			HttpServletRequest request) {

		return response(HttpStatus.UNAUTHORIZED, exception.getCode(), exception.getMessage(), request.getRequestURI(),
				null);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {

		HttpStatus status = switch (exception.getCode()) {
		case "QUESTION_CONCURRENT_MODIFICATION", "USER_CONCURRENT_MODIFICATION", "DATA_CONFLICT",
				"CATEGORY_CONCURRENT_MODIFICATION", "COLLECTION_CONCURRENT_MODIFICATION", "CATEGORY_ALREADY_EXISTS",
				"COLLECTION_ALREADY_EXISTS" ->
			HttpStatus.CONFLICT;
		case "QUESTION_NOT_FOUND", "CATEGORY_NOT_FOUND", "COLLECTION_NOT_FOUND", "USER_NOT_FOUND" ->
			HttpStatus.NOT_FOUND;
		default -> HttpStatus.BAD_REQUEST;
		};

		return response(status, exception.getCode(), exception.getMessage(), request.getRequestURI(), null);
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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {

		LOGGER.error("Unexpected error on {}", request.getRequestURI(), exception);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				"Ocurrió un error interno. Intenta nuevamente.", request.getRequestURI(), null);
	}

	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, String path,
			Map<String, String> fieldErrors) {

		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(Instant.now(), status.value(), code, message, path, fieldErrors));
	}
}
