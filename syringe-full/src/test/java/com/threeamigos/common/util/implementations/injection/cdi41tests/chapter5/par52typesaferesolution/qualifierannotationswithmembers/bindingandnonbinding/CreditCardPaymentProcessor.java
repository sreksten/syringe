package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding;

import jakarta.enterprise.context.Dependent;

@Dependent
@PayBy(PaymentMethod.CREDIT_CARD)
public class CreditCardPaymentProcessor implements PaymentProcessor {

    @Override
    public String kind() {
        return "CREDIT_CARD";
    }
}
