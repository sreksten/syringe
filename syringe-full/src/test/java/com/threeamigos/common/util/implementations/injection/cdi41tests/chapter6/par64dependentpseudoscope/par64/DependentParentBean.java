package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class DependentParentBean {

    @Inject
    private InvocationScopedDependentBean child;

    public String childId() {
        return child.id();
    }
}
