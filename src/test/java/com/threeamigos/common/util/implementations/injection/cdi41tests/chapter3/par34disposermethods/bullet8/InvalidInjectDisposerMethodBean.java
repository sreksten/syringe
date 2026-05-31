package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
public class InvalidInjectDisposerMethodBean {

    @Produces
    String produceValue() {
        return "value";
    }

    @Inject
    void disposeInvalid(@Disposes String value) {
    }
}
