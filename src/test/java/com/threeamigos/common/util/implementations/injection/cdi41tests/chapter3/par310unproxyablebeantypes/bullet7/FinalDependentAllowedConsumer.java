package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet7;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class FinalDependentAllowedConsumer {

    @Inject
    FinalDependentBeanWithoutProxyRequirement bean;

    public String invoke() {
        return bean.ping();
    }
}
