package com.fitpilot.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.ConstraintViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleLoginLimit(LoginRateLimitedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setProperty("retryAfterSeconds", exception.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds())).body(problem);
    }

    @ExceptionHandler(LoginProtectionUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleLoginUnavailable(LoginProtectionUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
    }

    @ExceptionHandler(AiNutritionException.class)
    public ProblemDetail handleAiNutrition(AiNutritionException exception) {
        HttpStatus status = exception.isUnavailable()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleResourceConflict(ResourceConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body contains invalid values."
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request parameters contain invalid values."
        );
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            fields.put(field.substring(field.lastIndexOf('.') + 1), violation.getMessage());
        });
        problem.setProperty("fieldErrors", fields);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "请求体不能为空，且必须是格式正确的 JSON；请检查日期、数字和选项。");
        // Report field paths only. Jackson exception messages can contain passwords or other rejected values.
        Throwable cause = exception.getCause();
        if (cause instanceof JsonMappingException mapping) {
            StringBuilder field = new StringBuilder();
            mapping.getPath().forEach(reference -> {
                if (reference.getFieldName() != null) {
                    if (!field.isEmpty()) field.append('.');
                    field.append(reference.getFieldName());
                } else if (reference.getIndex() >= 0) {
                    field.append('[').append(reference.getIndex()).append(']');
                }
            });
            if (!field.isEmpty()) problem.setProperty("fieldErrors", Map.of(field.toString(), "字段类型或格式不正确。"));
        }
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : "parameter";
        Class<?> type = exception.getRequiredType();
        String hint = type == LocalDate.class ? "必须是有效日期，格式为 YYYY-MM-DD。"
                : type == LocalDateTime.class ? "必须是有效日期时间，格式为 YYYY-MM-DDTHH:mm:ss。"
                : type == Long.class || type == long.class || type == Integer.class || type == int.class
                ? "必须是有效整数，且不能超出允许范围。" : "类型或格式不正确。";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "参数 " + field + " " + hint);
        problem.setProperty("fieldErrors", Map.of(field, hint));
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "缺少必填参数 " + exception.getParameterName() + "。");
        problem.setProperty("fieldErrors", Map.of(exception.getParameterName(), "必填参数不能为空。"));
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                exception.isForReturnValue() ? "服务响应暂时不可用，请稍后重试。" : "请求参数不符合要求。");
        if (!exception.isForReturnValue()) {
            Map<String, String> fields = new LinkedHashMap<>();
            exception.getParameterValidationResults().forEach(result -> {
                String name = result.getMethodParameter().getParameterName();
                fields.put(name == null ? "parameter" : name, "参数值不符合要求。");
            });
            problem.setProperty("fieldErrors", fields);
        }
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "无权访问该资源。");
    }
}
