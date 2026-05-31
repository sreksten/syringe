package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
public class InvalidInjectProducerMethodFactory {

    @Inject
    @Produces
    String produceInvalidType() {
        return null;
    }
}
