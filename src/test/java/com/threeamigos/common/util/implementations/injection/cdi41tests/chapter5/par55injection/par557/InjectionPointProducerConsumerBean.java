package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InjectionPointProducerConsumerBean {

    @Inject
    private ProducedInjectionPointPayload producedInjectionPointPayload;

    public ProducedInjectionPointPayload getProducedInjectionPointPayload() {
        return producedInjectionPointPayload;
    }
}
