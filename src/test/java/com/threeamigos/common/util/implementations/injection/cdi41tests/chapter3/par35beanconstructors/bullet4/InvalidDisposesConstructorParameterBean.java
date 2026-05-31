package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Inject;

@Dependent
public class InvalidDisposesConstructorParameterBean {

    @Inject
    InvalidDisposesConstructorParameterBean(@Disposes String eventPayload) {
    }
}
