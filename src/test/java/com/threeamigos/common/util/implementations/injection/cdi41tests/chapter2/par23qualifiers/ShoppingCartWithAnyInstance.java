package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

public class ShoppingCartWithAnyInstance {

    @Inject
    @Any
    private Instance<PaymentProcessor> paymentProcessorInstance;

    public Instance<PaymentProcessor> getPaymentProcessorInstance() {
        return paymentProcessorInstance;
    }
}
