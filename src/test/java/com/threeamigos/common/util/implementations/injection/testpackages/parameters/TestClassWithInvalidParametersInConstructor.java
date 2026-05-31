package com.threeamigos.common.util.implementations.injection.testpackages.parameters;

import jakarta.inject.Inject;

public class TestClassWithInvalidParametersInConstructor {

    @SuppressWarnings("all")
    private final int count;

    @Inject
    @SuppressWarnings("all")
    public TestClassWithInvalidParametersInConstructor(int count) {
        this.count = count;
    }
}
