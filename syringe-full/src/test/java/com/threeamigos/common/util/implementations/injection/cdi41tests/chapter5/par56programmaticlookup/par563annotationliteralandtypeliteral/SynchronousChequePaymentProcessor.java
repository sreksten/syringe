package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral;

@Synchronous
@PayBy(PaymentMethod.CHEQUE)
public class SynchronousChequePaymentProcessor implements MethodPaymentProcessor {

    @Override
    public String process() {
        return "sync-cheque";
    }
}
