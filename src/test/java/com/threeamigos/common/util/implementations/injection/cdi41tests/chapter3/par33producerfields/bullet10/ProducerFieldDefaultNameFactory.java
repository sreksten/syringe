package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@Dependent
public class ProducerFieldDefaultNameFactory {

    @Produces
    @Named
    PaymentProcessor paymentProcessor = null;

    public interface PaymentProcessor {
    }
}
