package com.threeamigos.common.util.implementations.injection.testpackages.optional;

/**
 * Concrete implementation of OptionalService for testing.
 */
public class OptionalServiceImpl implements OptionalService {
    @Override
    public String getValue() {
        return "OptionalService is present";
    }
}
