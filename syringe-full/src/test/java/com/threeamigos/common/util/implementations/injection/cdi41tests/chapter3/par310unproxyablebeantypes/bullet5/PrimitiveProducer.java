package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet5;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class PrimitiveProducer {

    @Produces
    @ApplicationScoped
    public int primitiveProduct() {
        return 42;
    }
}
