package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.inject.Inject;

public class ShoppingCart {

    @Inject
    @Reliable @Synchronous
    private PaymentProcessor paymentProcessor;

    public String processPayment() {
        return paymentProcessor.processPayment();
    }
}
