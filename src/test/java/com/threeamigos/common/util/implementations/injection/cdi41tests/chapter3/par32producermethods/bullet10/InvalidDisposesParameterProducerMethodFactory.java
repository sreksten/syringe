package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidDisposesParameterProducerMethodFactory {

    @Produces
    String produceInvalidType(@Disposes String value) {
        return null;
    }
}
