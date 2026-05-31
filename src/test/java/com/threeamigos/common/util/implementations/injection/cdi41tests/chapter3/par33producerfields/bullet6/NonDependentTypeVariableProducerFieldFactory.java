package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet6;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@Dependent
public class NonDependentTypeVariableProducerFieldFactory<T> {

    @Produces
    @ApplicationScoped
    List<T> value = null;
}
