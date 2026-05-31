package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidObservesParameterProducerMethodFactory {

    @Produces
    String produceInvalidType(@Observes String event) {
        return null;
    }
}
