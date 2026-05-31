package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class NonDependentNullProducerFieldFactory {

    @Produces
    @ApplicationScoped
    String runtimeValue = null;
}
