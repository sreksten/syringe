package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class PrimitiveConsumer {

    @Inject
    int value;
}
