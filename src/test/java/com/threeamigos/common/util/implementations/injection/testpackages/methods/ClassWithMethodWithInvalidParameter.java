package com.threeamigos.common.util.implementations.injection.testpackages.methods;

import jakarta.inject.Inject;

public class ClassWithMethodWithInvalidParameter {

    @SuppressWarnings("all")
    private int field;

    @Inject
    @SuppressWarnings("all")
    private void methodWithInvalidParameter(int field) {
        this.field = field;
    }

}
