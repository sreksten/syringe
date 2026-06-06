package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public abstract class InvalidAbstractInitializerMethodBean {

    @Inject
    abstract void initialize(AbstractInitializerDependency dependency);

    @Dependent
    public static class AbstractInitializerDependency {
    }
}
