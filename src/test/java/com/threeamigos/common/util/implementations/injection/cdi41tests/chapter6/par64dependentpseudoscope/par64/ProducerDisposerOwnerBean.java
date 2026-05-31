package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ProducerDisposerOwnerBean {

    @Produces
    ProducedPayload produce(InvocationScopedDependentBean dependent) {
        DependentInvocationRecorder.recordProducerParameter(dependent.id());
        return new ProducedPayload("produced");
    }

    void dispose(@Disposes ProducedPayload payload, InvocationScopedDependentBean dependent) {
        DependentInvocationRecorder.recordDisposerParameter(dependent.id());
    }
}
