package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@Dependent
public class MethodPriorityProducerFactory {

    @Produces
    @Alternative
    @Priority(100)
    public MethodPriorityService alternativeService() {
        return () -> "methodPriorityAlternative";
    }
}
