package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet6;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ArrayProducer {

    @Produces
    @ApplicationScoped
    public String[] arrayProduct() {
        return new String[]{"a", "b"};
    }
}
