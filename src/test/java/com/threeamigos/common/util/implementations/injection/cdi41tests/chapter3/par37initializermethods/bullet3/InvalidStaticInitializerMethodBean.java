package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet3;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InvalidStaticInitializerMethodBean {

    @Inject
    static void initialize(StaticInitializerDependency dependency) {
    }

    @Dependent
    public static class StaticInitializerDependency {
    }
}
