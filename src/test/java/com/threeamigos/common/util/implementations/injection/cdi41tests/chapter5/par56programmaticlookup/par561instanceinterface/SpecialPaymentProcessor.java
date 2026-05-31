package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

@Special
public class SpecialPaymentProcessor implements PaymentProcessor {

    @Override
    public String process() {
        return "special";
    }
}
