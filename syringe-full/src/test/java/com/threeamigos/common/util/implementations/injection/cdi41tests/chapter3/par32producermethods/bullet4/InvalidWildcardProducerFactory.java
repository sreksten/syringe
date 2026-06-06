package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@Dependent
public class InvalidWildcardProducerFactory {

    @Produces
    List<? extends Number> produceInvalidType() {
        return null;
    }
}
