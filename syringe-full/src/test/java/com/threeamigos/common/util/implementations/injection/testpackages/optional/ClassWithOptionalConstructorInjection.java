package com.threeamigos.common.util.implementations.injection.testpackages.optional;

import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Test class with optional constructor injection.
 */
public class ClassWithOptionalConstructorInjection {
    private final Optional<OptionalService> optionalService;
    private final Optional<NonExistentService> nonExistentService;

    @Inject
    public ClassWithOptionalConstructorInjection(
            Optional<OptionalService> optionalService,
            Optional<NonExistentService> nonExistentService) {
        this.optionalService = optionalService;
        this.nonExistentService = nonExistentService;
    }

    public Optional<OptionalService> getOptionalService() {
        return optionalService;
    }

    public Optional<NonExistentService> getNonExistentService() {
        return nonExistentService;
    }
}
