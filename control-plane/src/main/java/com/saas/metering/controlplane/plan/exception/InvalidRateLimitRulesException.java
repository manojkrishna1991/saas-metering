package com.saas.metering.controlplane.plan.exception;

public class InvalidRateLimitRulesException extends RuntimeException {

    public InvalidRateLimitRulesException(String message) {
        super(message);
    }
}
