package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ArrayFieldTypeProducerFactory {

    @Produces
    String[] producedArray = new String[0];
}
