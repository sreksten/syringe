package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.wrapperfromprimitive;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class WrapperFromPrimitiveProducerBean {

    @Produces
    public int producePrimitiveValue() {
        return 73;
    }
}
