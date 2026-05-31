package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
public class MethodProducerFactory {

    @Produces
    @Named
    MethodProducedType getMethodProducedType() {
        return new MethodProducedType();
    }
}
