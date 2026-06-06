package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet11;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class MultiParameterInitializerMethodBean {

    public static int initializerCalls = 0;

    private DependencyA dependencyA;
    private DependencyB dependencyB;
    private DependencyC dependencyC;

    @Inject
    void initialize(DependencyA dependencyA, DependencyB dependencyB, DependencyC dependencyC) {
        this.dependencyA = dependencyA;
        this.dependencyB = dependencyB;
        this.dependencyC = dependencyC;
        initializerCalls++;
    }

    public static void reset() {
        initializerCalls = 0;
    }

    public DependencyA getDependencyA() {
        return dependencyA;
    }

    public DependencyB getDependencyB() {
        return dependencyB;
    }

    public DependencyC getDependencyC() {
        return dependencyC;
    }

    @Dependent
    public static class DependencyA {
    }

    @Dependent
    public static class DependencyB {
    }

    @Dependent
    public static class DependencyC {
    }
}
