package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.primitivewrapperequivalence;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class PrimitiveWrapperEquivalenceConsumer {

    @Inject
    private int primitiveValue;

    @Inject
    private Integer wrapperValue;

    public int getPrimitiveValue() {
        return primitiveValue;
    }

    public Integer getWrapperValue() {
        return wrapperValue;
    }
}
