package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par554;

public class StaticProducedPayload {

    private final String producerDependencyId;

    public StaticProducedPayload(String producerDependencyId) {
        this.producerDependencyId = producerDependencyId;
    }

    public String getProducerDependencyId() {
        return producerDependencyId;
    }
}
