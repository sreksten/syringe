package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidObservesAsyncParameterDisposerMethodBean {

    @Produces
    String produceValue() {
        return "value";
    }

    void disposeInvalid(@Disposes String value, @ObservesAsync String event) {
    }
}
