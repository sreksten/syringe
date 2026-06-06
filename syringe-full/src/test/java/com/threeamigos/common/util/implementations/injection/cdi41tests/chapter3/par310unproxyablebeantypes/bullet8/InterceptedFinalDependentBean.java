package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet8;

import jakarta.enterprise.context.Dependent;

@Dependent
@UnproxyableInterceptorBinding
public final class InterceptedFinalDependentBean {

    public String businessMethod() {
        return "intercepted";
    }
}
