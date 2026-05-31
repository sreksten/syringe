package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet6;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ArrayConsumer {

    @Inject
    String[] values;
}
