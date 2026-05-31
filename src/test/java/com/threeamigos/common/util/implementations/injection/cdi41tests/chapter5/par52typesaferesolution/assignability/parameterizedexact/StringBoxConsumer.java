package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.parameterizedexact;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class StringBoxConsumer {

    @Inject
    private Box<String> box;

    public Box<String> getBox() {
        return box;
    }
}
