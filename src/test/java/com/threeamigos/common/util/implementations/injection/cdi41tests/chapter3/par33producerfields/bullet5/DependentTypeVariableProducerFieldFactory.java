package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@Dependent
public class DependentTypeVariableProducerFieldFactory<T> {

    @Produces
    @Dependent
    List<T> value = null;
}
