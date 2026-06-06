package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@Dependent
public class DependentTypeVariableProducerFactory<T> {

    @Produces
    @Dependent
    List<T> produceValue() {
        return null;
    }
}
