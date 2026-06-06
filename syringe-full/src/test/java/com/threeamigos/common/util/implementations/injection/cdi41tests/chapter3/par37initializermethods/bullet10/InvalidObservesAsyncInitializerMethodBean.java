package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@Dependent
public class InvalidObservesAsyncInitializerMethodBean {

    @Inject
    void initialize(@ObservesAsync String eventPayload) {
    }
}
