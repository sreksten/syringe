package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidObservesAsyncParameterProducerMethodFactory {

    @Produces
    String produceInvalidType(@ObservesAsync String event) {
        return null;
    }
}
