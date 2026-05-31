package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet9;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class PrivateConstructorConsumer {

    @Inject
    PrivateConstructorNormalScopedBean bean;
}
