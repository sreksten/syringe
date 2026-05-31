package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ParentWithProducerAndDisposerMethods {

    public static int disposeCalls;

    @Produces
    ProducedByMethod produceProduct() {
        return new ProducedByMethod("parent-method");
    }

    void disposeProduct(@Disposes ProducedByMethod product) {
        disposeCalls++;
    }

    public static void reset() {
        disposeCalls = 0;
    }
}
