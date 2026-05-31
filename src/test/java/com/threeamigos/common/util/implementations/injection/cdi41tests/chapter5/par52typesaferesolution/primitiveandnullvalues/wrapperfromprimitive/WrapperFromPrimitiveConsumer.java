package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.wrapperfromprimitive;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class WrapperFromPrimitiveConsumer {

    @Inject
    private Integer wrapperValue;

    public Integer getWrapperValue() {
        return wrapperValue;
    }
}
