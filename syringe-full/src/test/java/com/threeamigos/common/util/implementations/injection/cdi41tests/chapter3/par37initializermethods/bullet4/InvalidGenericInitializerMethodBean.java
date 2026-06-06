package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InvalidGenericInitializerMethodBean {

    @Inject
    <T> void initialize(T value) {
    }
}
