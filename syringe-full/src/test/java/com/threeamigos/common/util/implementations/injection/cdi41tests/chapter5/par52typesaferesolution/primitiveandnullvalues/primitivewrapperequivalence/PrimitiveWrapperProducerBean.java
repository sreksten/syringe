package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.primitivewrapperequivalence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class PrimitiveWrapperProducerBean {

    @Produces
    public Integer produceIntegerValue() {
        return 42;
    }
}
