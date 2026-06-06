package com.threeamigos.common.util.implementations.injection.legacynewfixtures;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LegacyNewTargetBean {

    public String id() {
        return Integer.toHexString(System.identityHashCode(this));
    }
}
