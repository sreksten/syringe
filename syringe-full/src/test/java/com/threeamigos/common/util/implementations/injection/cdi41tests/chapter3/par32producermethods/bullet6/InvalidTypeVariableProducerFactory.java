package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet6;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidTypeVariableProducerFactory<T> {

    @Produces
    T produceInvalidType() {
        return null;
    }
}
