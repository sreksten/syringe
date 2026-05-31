package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet5;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@Dependent
public class NonDependentTypeVariableProducerFactory<T> {

    @Produces
    @ApplicationScoped
    List<T> produceValue() {
        return null;
    }
}
