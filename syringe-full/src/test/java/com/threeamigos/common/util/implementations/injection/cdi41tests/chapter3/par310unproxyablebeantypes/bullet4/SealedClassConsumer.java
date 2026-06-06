package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class SealedClassConsumer {

    @Inject
    java.nio.Buffer buffer;
}
