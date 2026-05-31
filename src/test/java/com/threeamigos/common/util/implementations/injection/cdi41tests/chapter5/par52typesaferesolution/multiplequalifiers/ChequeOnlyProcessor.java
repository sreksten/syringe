package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers;

import jakarta.enterprise.context.Dependent;

@Dependent
@PayByMulti(PaymentMethodMulti.CHEQUE)
public class ChequeOnlyProcessor implements PaymentProcessorMulti {

    @Override
    public String name() {
        return "cheque-only";
    }
}
