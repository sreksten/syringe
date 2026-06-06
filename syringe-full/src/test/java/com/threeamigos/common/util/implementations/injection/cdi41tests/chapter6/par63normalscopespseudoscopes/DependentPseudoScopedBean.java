package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par63normalscopespseudoscopes;

import jakarta.enterprise.context.Dependent;

@Dependent
public class DependentPseudoScopedBean {
    private final String id = Integer.toHexString(System.identityHashCode(this));

    String id() {
        return id;
    }
}
