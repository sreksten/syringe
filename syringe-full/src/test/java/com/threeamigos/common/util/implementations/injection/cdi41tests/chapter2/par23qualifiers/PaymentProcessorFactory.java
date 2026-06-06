package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.enterprise.inject.Produces;

public class PaymentProcessorFactory {

    private boolean isAsync = false;

    public void setAsync(boolean isAsync) {
        this.isAsync = isAsync;
    }

    @Produces @FactoryProduced
    public PaymentProcessor getPaymentProcessor(@Synchronous @Reliable PaymentProcessor synchronousPaymentProcessor,
                                                @Asynchronous PaymentProcessor asynchronousPaymentProcessor) {
        return isAsync ? asynchronousPaymentProcessor : synchronousPaymentProcessor;
    }
}
