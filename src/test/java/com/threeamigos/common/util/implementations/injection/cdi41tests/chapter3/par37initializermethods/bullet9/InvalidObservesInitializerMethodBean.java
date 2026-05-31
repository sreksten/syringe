package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet9;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@Dependent
public class InvalidObservesInitializerMethodBean {

    @Inject
    void initialize(@Observes String eventPayload) {
    }
}
