package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.nulltoprimitivedefault;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class NullToPrimitiveDefaultProducerBean {

    @Produces
    public Integer produceNullableInteger() {
        return null;
    }
}
