package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class DependentNullProducerFactory {

    @Produces
    @Dependent
    String produceRuntimeValue() {
        return null;
    }
}
