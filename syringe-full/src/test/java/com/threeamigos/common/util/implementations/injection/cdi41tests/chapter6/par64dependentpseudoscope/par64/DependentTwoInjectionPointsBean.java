package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DependentTwoInjectionPointsBean {

    @Inject
    private InvocationScopedDependentBean first;

    @Inject
    private InvocationScopedDependentBean second;

    public String firstId() {
        return first.id();
    }

    public String secondId() {
        return second.id();
    }
}
