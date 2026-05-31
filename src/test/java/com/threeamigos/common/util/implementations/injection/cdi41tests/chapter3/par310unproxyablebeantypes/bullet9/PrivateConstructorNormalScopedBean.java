package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet9;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PrivateConstructorNormalScopedBean {

    private PrivateConstructorNormalScopedBean() {
        // Intentionally private to validate unproxyable client proxy behavior.
    }
}
