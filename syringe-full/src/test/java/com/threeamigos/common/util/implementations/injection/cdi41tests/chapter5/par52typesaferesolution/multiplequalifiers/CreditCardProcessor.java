package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers;

import jakarta.enterprise.context.Dependent;

@Dependent
@PayByMulti(PaymentMethodMulti.CREDIT_CARD)
public class CreditCardProcessor implements PaymentProcessorMulti {

    @Override
    public String name() {
        return "credit-card";
    }
}
