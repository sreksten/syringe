package com.threeamigos.common.util.implementations.injection;

public class NotEnabledFeatureException extends RuntimeException {
    public NotEnabledFeatureException(String message) {
        super(message);
    }
}
