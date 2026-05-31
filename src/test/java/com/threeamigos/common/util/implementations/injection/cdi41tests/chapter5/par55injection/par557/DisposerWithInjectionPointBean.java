package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

@Dependent
public class DisposerWithInjectionPointBean {

    @Produces
    ProducedInjectionPointPayload producePayload() {
        return new ProducedInjectionPointPayload("payload");
    }

    void disposePayload(@Disposes ProducedInjectionPointPayload payload, InjectionPoint injectionPoint) {
    }
}
