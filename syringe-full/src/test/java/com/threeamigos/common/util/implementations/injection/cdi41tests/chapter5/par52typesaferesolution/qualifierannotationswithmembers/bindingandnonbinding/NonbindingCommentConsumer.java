package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class NonbindingCommentConsumer {

    @Inject
    @PayBy(value = PaymentMethod.CHEQUE, comment = "injection-point-comment")
    private PaymentProcessor paymentProcessor;

    public PaymentProcessor getPaymentProcessor() {
        return paymentProcessor;
    }
}
