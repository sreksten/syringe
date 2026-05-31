package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class DeclaringClassPriorityClient {

    @Inject
    private DeclaringClassPriorityService service;

    public String serviceType() {
        return service.type();
    }
}
