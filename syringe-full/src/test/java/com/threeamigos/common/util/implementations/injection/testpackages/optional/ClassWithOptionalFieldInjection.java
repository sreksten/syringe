package com.threeamigos.common.util.implementations.injection.testpackages.optional;

import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Test class with optional field injection.
 */
public class ClassWithOptionalFieldInjection {
    @Inject
    private Optional<OptionalService> optionalService;

    @Inject
    private Optional<NonExistentService> nonExistentService;

    public Optional<OptionalService> getOptionalService() {
        return optionalService;
    }

    public Optional<NonExistentService> getNonExistentService() {
        return nonExistentService;
    }
}
