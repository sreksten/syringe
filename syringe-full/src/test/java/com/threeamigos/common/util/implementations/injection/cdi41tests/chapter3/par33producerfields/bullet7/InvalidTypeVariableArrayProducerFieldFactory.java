package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet7;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidTypeVariableArrayProducerFieldFactory<T> {

    @Produces
    T[] value = null;
}
