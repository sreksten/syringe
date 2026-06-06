package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class QualifierAwareDisposerBean {

    public static int fastDisposeCount = 0;
    public static int slowDisposeCount = 0;

    public static void reset() {
        fastDisposeCount = 0;
        slowDisposeCount = 0;
    }

    @Produces
    @FastDisposerQualifier
    PaymentProcessor produceFast() {
        return new FastProcessor();
    }

    @Produces
    @SlowDisposerQualifier
    PaymentProcessor produceSlow() {
        return new SlowProcessor();
    }

    void disposeFast(@Disposes @FastDisposerQualifier PaymentProcessor processor) {
        fastDisposeCount++;
    }

    void disposeSlow(@Disposes @SlowDisposerQualifier PaymentProcessor processor) {
        slowDisposeCount++;
    }

    public interface PaymentProcessor {
    }

    public static class FastProcessor implements PaymentProcessor {
    }

    public static class SlowProcessor implements PaymentProcessor {
    }
}
