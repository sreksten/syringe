package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.nulltoprimitivedefault;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class NullToPrimitiveDefaultConsumer {

    @Inject
    private int primitiveIntValue;

    public int getPrimitiveIntValue() {
        return primitiveIntValue;
    }
}
