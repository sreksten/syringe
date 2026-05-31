package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidMultipleDisposesParametersBean {

    @Produces
    String produceValue() {
        return "value";
    }

    void disposeInvalid(@Disposes String first, @Disposes String second) {
    }
}
