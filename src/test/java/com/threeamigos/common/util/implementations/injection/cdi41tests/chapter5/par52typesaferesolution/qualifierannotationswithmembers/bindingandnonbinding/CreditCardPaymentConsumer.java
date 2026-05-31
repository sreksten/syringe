package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class CreditCardPaymentConsumer {

    @Inject
    @PayBy(PaymentMethod.CREDIT_CARD)
    private PaymentProcessor paymentProcessor;

    public PaymentProcessor getPaymentProcessor() {
        return paymentProcessor;
    }
}
