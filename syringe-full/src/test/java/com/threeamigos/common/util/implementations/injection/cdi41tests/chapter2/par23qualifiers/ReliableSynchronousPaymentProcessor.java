package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

@Reliable @Synchronous
public class ReliableSynchronousPaymentProcessor implements PaymentProcessor {

    @Override
    public String processPayment() {
        return "Processed synchronously and reliably";
    }
}
