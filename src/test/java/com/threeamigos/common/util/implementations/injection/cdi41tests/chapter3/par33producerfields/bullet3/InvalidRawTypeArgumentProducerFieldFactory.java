package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet3;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;
import java.util.Map;

@Dependent
public class InvalidRawTypeArgumentProducerFieldFactory {

    @Produces
    List<Map> invalidValue = null;
}
