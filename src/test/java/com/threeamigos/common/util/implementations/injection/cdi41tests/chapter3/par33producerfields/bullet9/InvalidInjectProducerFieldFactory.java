package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet9;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
public class InvalidInjectProducerFieldFactory {

    @Inject
    @Produces
    String invalidProduct = "invalid";
}
