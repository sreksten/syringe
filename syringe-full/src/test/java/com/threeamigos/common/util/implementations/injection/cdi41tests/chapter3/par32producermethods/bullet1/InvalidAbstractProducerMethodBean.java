package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public abstract class InvalidAbstractProducerMethodBean {

    @Produces
    protected abstract String produceValue();
}
