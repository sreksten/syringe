package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@Dependent
public class DynamicInstanceInjectionPointConsumerBean {

    @Inject
    private Instance<InjectionPoint> payloadInstance;

    @Inject
    private transient Instance<InjectionPoint> transientPayloadInstance;

    public InjectionPoint getDefaultPayload() {
        return payloadInstance.get();
    }

    public InjectionPoint getRedPayload() {
        return payloadInstance.select(DynamicRedLiteral.INSTANCE).get();
    }

    public InjectionPoint getTransientPayload() {
        return transientPayloadInstance.get();
    }
}
