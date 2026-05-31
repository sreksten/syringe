package com.threeamigos.common.util.implementations.injection.discovery;

import jakarta.enterprise.inject.spi.DefinitionException;

/**
 * Raised when Syringe encounters non-portable CDI behavior that this implementation
 * chooses to reject as a hard validation error.
 */
public class NonPortableBehaviourException extends DefinitionException {

    public NonPortableBehaviourException(String message) {
        super(message);
    }

    public NonPortableBehaviourException(String message, Throwable cause) {
        super(message, cause);
    }
}
