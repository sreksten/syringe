package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;

/**
 * Exception thrown when a feature is not enabled for a specific module.
 *
 * @author Stefano Reksten
 */
public class NotEnabledFeatureException extends RuntimeException {

    public NotEnabledFeatureException(String message, ModulesEnum module) {
        super(message + " Add syringe-" + module.name() + " to your classpath to enable support.");
    }

}
