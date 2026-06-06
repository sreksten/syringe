package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.rawtoparameterized;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@SuppressWarnings("rawtypes")
@Dependent
public class RawProducer {

    @Produces
    public Foo produceFoo() {
        return new Foo<Integer>(1);
    }

    @Produces
    public Bar produceBar() {
        return new Bar<Integer, String>(1);
    }
}
