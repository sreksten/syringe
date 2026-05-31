package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

public class LiteralSelectionConsumer {

    @Inject
    @Any
    private Instance<MethodPaymentProcessor> anyMethodProcessors;

    @Inject
    @Any
    private Instance<GenericPaymentProcessor<?>> anyGenericProcessors;

    public String getSynchronousPaymentProcessor(PaymentMethod paymentMethod) {
        class SynchronousQualifier extends AnnotationLiteral<Synchronous> implements Synchronous {
        }
        class PayByQualifier extends AnnotationLiteral<PayBy> implements PayBy {
            @Override
            public PaymentMethod value() {
                return paymentMethod;
            }
        }

        return anyMethodProcessors
                .select(new SynchronousQualifier(), new PayByQualifier())
                .get()
                .process();
    }

    public String getChequePaymentProcessorViaTypeLiteral() {
        return anyGenericProcessors
                .select(new TypeLiteral<GenericPaymentProcessor<Cheque>>() {})
                .get()
                .process();
    }
}
