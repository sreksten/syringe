package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

@Synchronous
public class SynchronousPaymentProcessor implements PaymentProcessor {

    @Override
    public String processPayment() {
        return "Processed synchronously";
    }
}
