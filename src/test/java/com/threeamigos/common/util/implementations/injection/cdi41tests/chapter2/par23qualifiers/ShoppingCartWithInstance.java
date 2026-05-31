package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

public class ShoppingCartWithInstance {

    @Inject
    @Reliable @Synchronous
    private Instance<PaymentProcessor> paymentProcessorInstance;

    public String processPayment() {
        return paymentProcessorInstance.get().processPayment();
    }
}
