package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par554;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class StaticProducerDisposerBean {

    @Produces
    public static StaticProducedPayload produce(InvocationDependentParam dependency) {
        ProducerInvocationRecorder.record("static-producer:" + dependency.getId());
        return new StaticProducedPayload(dependency.getId());
    }

    public static void dispose(@Disposes StaticProducedPayload payload, InvocationDependentParam dependency) {
        ProducerInvocationRecorder.record(
                "static-disposer:" + payload.getProducerDependencyId() + ":" + dependency.getId()
        );
    }
}
