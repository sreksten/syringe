package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet7;

import jakarta.enterprise.context.Dependent;

@Dependent
public final class FinalDependentBeanWithoutProxyRequirement {

    public String ping() {
        return "ok";
    }
}
