package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.highestpriority;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ConsumerBeanHighestPriority {

    @Inject
    private ResolutionServiceHighestPriority service;

    public ResolutionServiceHighestPriority getService() {
        return service;
    }
}
