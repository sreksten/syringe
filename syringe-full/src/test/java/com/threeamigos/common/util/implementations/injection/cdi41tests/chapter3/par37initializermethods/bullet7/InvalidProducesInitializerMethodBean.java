package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet7;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
public class InvalidProducesInitializerMethodBean {

    @Inject
    @Produces
    String initialize(InitializerDependency dependency) {
        return "invalid";
    }

    @Dependent
    public static class InitializerDependency {
    }
}
