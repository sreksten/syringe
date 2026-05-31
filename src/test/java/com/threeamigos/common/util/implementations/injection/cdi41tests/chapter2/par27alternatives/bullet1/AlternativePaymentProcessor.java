package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Dependent
public class AlternativePaymentProcessor implements PaymentProcessor {
    @Override
    public String type() {
        return "alternative";
    }
}
