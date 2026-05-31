package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@Dependent
public class InvalidWildcardProducerFieldFactory {

    @Produces
    List<? extends Number> invalidValue = null;
}
