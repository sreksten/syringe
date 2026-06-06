package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par63normalscopespseudoscopes;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SharedNormalScopedBean {
    private final String id = Integer.toHexString(System.identityHashCode(this));

    public String id() {
        return id;
    }
}
