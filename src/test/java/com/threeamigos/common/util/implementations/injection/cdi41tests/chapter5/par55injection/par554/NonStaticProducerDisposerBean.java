package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par554;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

import java.util.UUID;

@Dependent
public class NonStaticProducerDisposerBean {

    private final String instanceId = UUID.randomUUID().toString();

    @Produces
    public NonStaticProducedPayload produce(InvocationDependentParam dependency) {
        ProducerInvocationRecorder.record("nonstatic-producer:" + instanceId + ":" + dependency.getId());
        return new NonStaticProducedPayload(instanceId, dependency.getId());
    }

    public void dispose(@Disposes NonStaticProducedPayload payload, InvocationDependentParam dependency) {
        ProducerInvocationRecorder.record(
                "nonstatic-disposer:" + instanceId + ":" + payload.getDeclaringBeanId() + ":" + dependency.getId()
        );
    }
}
