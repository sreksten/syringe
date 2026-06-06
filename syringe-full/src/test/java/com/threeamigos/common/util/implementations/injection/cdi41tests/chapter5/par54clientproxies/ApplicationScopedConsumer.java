package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par54clientproxies;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ApplicationScopedConsumer {

    @Inject
    private RequestScopedService requestScopedService;

    @Inject
    private DependentService dependentService;

    public RequestScopedService getRequestScopedService() {
        return requestScopedService;
    }

    public DependentService getDependentService() {
        return dependentService;
    }
}
