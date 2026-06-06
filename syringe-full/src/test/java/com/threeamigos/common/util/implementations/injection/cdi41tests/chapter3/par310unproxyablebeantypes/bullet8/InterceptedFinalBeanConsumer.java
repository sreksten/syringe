package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InterceptedFinalBeanConsumer {

    @Inject
    InterceptedFinalDependentBean bean;
}
