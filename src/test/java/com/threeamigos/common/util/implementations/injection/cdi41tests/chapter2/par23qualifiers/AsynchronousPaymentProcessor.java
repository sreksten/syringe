package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

@Asynchronous
public class AsynchronousPaymentProcessor implements PaymentProcessor {

    @Override
    public String processPayment() {
        return "Processed asynchronously";
    }
}
