package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class NonDependentNullProducerFactory {

    @Produces
    @ApplicationScoped
    String produceRuntimeValue() {
        return null;
    }
}
