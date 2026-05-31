package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidObservesParameterDisposerMethodBean {

    @Produces
    String produceValue() {
        return "value";
    }

    void disposeInvalid(@Disposes String value, @Observes String event) {
    }
}
