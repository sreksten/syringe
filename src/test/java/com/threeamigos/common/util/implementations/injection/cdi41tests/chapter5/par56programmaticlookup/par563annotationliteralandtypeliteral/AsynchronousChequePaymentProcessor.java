package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral;

@PayBy(PaymentMethod.CHEQUE)
public class AsynchronousChequePaymentProcessor implements MethodPaymentProcessor {

    @Override
    public String process() {
        return "async-cheque";
    }
}
