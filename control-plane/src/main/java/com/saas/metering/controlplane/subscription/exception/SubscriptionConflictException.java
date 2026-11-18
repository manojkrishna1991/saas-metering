package com.saas.metering.controlplane.subscription.exception;

public class SubscriptionConflictException extends RuntimeException {

    public SubscriptionConflictException(String message) {
        super(message);
    }
}
