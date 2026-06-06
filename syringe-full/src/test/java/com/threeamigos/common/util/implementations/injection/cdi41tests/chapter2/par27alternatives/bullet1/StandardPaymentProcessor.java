package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet1;

import jakarta.enterprise.context.Dependent;

@Dependent
public class StandardPaymentProcessor implements PaymentProcessor {
    @Override
    public String type() {
        return "standard";
    }
}
