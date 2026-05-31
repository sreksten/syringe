package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public abstract class InvalidAbstractDisposerMethodBean {

    @Produces
    String produceValue() {
        return "value";
    }

    abstract void disposeValue(@Disposes String value);
}
