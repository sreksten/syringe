package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.producerpriority;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ConsumerBeanProducerPriority {

    @Inject
    private ResolutionServiceProducerPriority service;

    public ResolutionServiceProducerPriority getService() {
        return service;
    }
}
