package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.explicit;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ExplicitPriorityClient {

    @Inject
    private ExplicitPriorityService explicitPriorityService;

    public String selectedServiceType() {
        return explicitPriorityService.serviceType();
    }
}
