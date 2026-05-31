package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet6;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
@InitializerMethodBinding
public class InitializerInterceptorBean {

    public static int initializerCalls = 0;
    private InitializerDependency dependency;

    @Inject
    public void initialize(InitializerDependency dependency) {
        this.dependency = dependency;
        initializerCalls++;
    }

    public void businessMethod() {
    }

    public static void reset() {
        initializerCalls = 0;
    }

    public InitializerDependency getDependency() {
        return dependency;
    }
}
