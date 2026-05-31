package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.nonalternativeelimination;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ConsumerBeanNonAlternativeElimination {

    @Inject
    private ResolutionService service;

    public ResolutionService getService() {
        return service;
    }
}
