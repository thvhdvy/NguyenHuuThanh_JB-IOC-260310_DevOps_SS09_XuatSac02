package com.quickbite.user_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> status(ResponseStatusException e, HttpServletRequest req) {
		return build(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason(), req);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiError> badRequest(Exception e, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "Malformed request", req);
	}

	@ExceptionHandler({ OptimisticLockingFailureException.class, DataIntegrityViolationException.class })
	public ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, "Conflicting update, please retry", req);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> other(Exception e, HttpServletRequest req) throws Exception {
		if (e instanceof org.springframework.web.ErrorResponse er) {
			// lỗi chuẩn của Spring MVC (404, 405, 415...) giữ nguyên status
			return build(HttpStatus.valueOf(er.getStatusCode().value()), er.getBody().getDetail(), req);
		}
		log.error("Unhandled error on {}", req.getRequestURI(), e);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
		return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
				message, req.getRequestURI()));
	}
}
