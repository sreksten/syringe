package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral;

public class CardGenericPaymentProcessor implements GenericPaymentProcessor<Card> {

    @Override
    public String process() {
        return "generic-card";
    }
}
