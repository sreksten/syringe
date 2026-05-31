package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet13;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.util.List;

@Dependent
public class ProducerMethodDefaultNameFactory {

    @Produces
    @Named
    public List<Product> getProducts() {
        return null;
    }

    @Produces
    @Named
    public PaymentProcessor paymentProcessor() {
        return null;
    }
}
