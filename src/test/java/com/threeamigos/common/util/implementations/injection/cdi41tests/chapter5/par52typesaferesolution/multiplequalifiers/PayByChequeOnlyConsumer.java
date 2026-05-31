package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class PayByChequeOnlyConsumer {

    @Inject
    @PayByMulti(PaymentMethodMulti.CHEQUE)
    private PaymentProcessorMulti paymentProcessor;

    public PaymentProcessorMulti getPaymentProcessor() {
        return paymentProcessor;
    }
}
