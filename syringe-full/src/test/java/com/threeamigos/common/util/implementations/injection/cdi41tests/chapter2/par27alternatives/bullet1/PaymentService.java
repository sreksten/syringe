package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class PaymentService {

    @Inject
    private PaymentProcessor paymentProcessor;

    public String processorType() {
        return paymentProcessor.type();
    }
}
