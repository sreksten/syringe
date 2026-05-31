package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Dependent
public class InvalidInjectProducesFieldBean {

    @Inject
    @Produces
    ProducedType producedType;

    @Dependent
    public static class ProducedType {
    }
}
