package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64.InvocationScopedDependentBean;
import jakarta.inject.Inject;

public class NonContextualDependentOwner {

    @Inject
    private InvocationScopedDependentBean dependent;

    public String dependentId() {
        return dependent.id();
    }
}
