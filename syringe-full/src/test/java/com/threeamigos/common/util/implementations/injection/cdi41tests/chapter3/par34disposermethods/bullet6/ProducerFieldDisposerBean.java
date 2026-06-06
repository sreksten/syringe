package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet6;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ProducerFieldDisposerBean {

    public static int disposeCount = 0;
    public static Object lastDisposedInstance = null;

    public static void reset() {
        disposeCount = 0;
        lastDisposedInstance = null;
    }

    @Produces
    PaymentProcessor producedProcessor = new FieldProcessor();

    void disposeProcessor(@Disposes PaymentProcessor processor) {
        disposeCount++;
        lastDisposedInstance = processor;
    }

    public interface PaymentProcessor {
    }

    public static class FieldProcessor implements PaymentProcessor {
    }
}
