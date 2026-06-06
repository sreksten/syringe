package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.ordering;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class OrderedServiceClient {

    @Inject
    private OrderedService orderedService;

    public String selectedServiceType() {
        return orderedService.serviceType();
    }
}
