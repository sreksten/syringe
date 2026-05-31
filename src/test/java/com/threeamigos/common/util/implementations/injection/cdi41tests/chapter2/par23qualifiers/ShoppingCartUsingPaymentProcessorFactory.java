package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.inject.Inject;

public class ShoppingCartUsingPaymentProcessorFactory {

    @Inject
    private PaymentProcessorFactory paymentProcessorFactory;

    @Inject
    @FactoryProduced
    private PaymentProcessor paymentProcessor;

    public String processPayment() {
        return paymentProcessor.processPayment();
    }
}
