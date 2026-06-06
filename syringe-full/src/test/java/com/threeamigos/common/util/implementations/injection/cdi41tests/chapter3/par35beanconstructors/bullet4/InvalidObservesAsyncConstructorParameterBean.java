package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@Dependent
public class InvalidObservesAsyncConstructorParameterBean {

    @Inject
    InvalidObservesAsyncConstructorParameterBean(@ObservesAsync String eventPayload) {
    }
}
