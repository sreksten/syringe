package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class CheckoutService {

    @Inject
    private Gateway gateway;

    public String gatewayType() {
        return gateway.type();
    }
}
