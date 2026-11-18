package com.saas.metering.controlplane.web;

import com.saas.metering.controlplane.plan.exception.InvalidRateLimitRulesException;
import com.saas.metering.controlplane.plan.exception.PlanAlreadyExistsException;
import com.saas.metering.controlplane.plan.exception.PlanNotFoundException;
import com.saas.metering.controlplane.subscription.exception.SubscriptionConflictException;
import com.saas.metering.controlplane.subscription.exception.SubscriptionNotFoundException;
import com.saas.metering.controlplane.tenant.exception.TenantAlreadyExistsException;
import com.saas.metering.controlplane.tenant.exception.TenantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .orElse("Validation failed");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or invalid request body");
    }

    @ExceptionHandler({TenantNotFoundException.class, PlanNotFoundException.class,
            SubscriptionNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({TenantAlreadyExistsException.class, PlanAlreadyExistsException.class,
            SubscriptionConflictException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidRateLimitRulesException.class)
    public ProblemDetail handleInvalidRules(InvalidRateLimitRulesException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }
}
