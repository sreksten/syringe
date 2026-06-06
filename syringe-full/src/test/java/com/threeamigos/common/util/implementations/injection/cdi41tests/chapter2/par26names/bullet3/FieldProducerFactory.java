package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
public class FieldProducerFactory {

    @Produces
    @Named
    FieldProducedType fieldProducedType = new FieldProducedType();
}
