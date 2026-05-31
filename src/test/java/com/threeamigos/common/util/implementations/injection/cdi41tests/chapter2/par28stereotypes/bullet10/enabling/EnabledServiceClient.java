package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.enabling;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class EnabledServiceClient {

    @Inject
    private EnabledService enabledService;

    public String selectedServiceType() {
        return enabledService.serviceType();
    }
}
