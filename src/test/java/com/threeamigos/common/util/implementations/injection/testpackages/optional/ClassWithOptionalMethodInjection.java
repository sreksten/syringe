package com.threeamigos.common.util.implementations.injection.testpackages.optional;

import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Test class with optional method injection.
 */
public class ClassWithOptionalMethodInjection {
    private Optional<OptionalService> optionalService;
    private Optional<NonExistentService> nonExistentService;

    @Inject
    public void setDependencies(
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
