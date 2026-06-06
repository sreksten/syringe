package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers;

import jakarta.enterprise.context.Dependent;

@Dependent
@SynchronousMulti
@PayByMulti(PaymentMethodMulti.CHEQUE)
public class ChequeSynchronousProcessor implements PaymentProcessorMulti {

    @Override
    public String name() {
        return "cheque-synchronous";
    }
}
