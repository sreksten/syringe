package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ProducerFieldStereotypePriorityClient {

    @Inject
    private ProducerFieldStereotypePriorityService service;

    public String serviceType() {
        return service.type();
    }
}
