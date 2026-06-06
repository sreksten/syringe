package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers;

import jakarta.enterprise.context.Dependent;

@Dependent
@SynchronousMulti
public class SynchronousOnlyProcessor implements PaymentProcessorMulti {

    @Override
    public String name() {
        return "synchronous-only";
    }
}
