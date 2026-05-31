package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding;

import jakarta.enterprise.context.Dependent;

@Dependent
@PayBy(value = PaymentMethod.CHEQUE, comment = "bean-comment")
public class ChequePaymentProcessor implements PaymentProcessor {

    @Override
    public String kind() {
        return "CHEQUE";
    }
}
