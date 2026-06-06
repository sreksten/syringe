package com.threeamigos.common.util.implementations.injection.testpackages.circulardependencies;

import jakarta.inject.Inject;

public class A {

    @Inject
    B b;

}
