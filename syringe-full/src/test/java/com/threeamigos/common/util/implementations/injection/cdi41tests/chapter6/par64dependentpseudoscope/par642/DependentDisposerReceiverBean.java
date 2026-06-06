package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.TransientReference;

@Dependent
public class DependentDisposerReceiverBean {

    @Produces
    ProducedByDependentDisposerOwner produce() {
        return new ProducedByDependentDisposerOwner();
    }

    void dispose(@Disposes ProducedByDependentDisposerOwner produced,
                 @TransientReference TransientReferenceDependentParam transientDependent) {
        DependentDestructionParamRecorder.recordDisposerParam(transientDependent.id());
    }

    @PreDestroy
    void preDestroy() {
        ReceiverInvocationRecorder.disposerReceiverDestroyed();
    }
}
