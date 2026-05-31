package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.TransientReference;

@Dependent
public class DependentObserverReceiverBean {

    void onEvent(@Observes DependentReceiverEvent event,
                 @TransientReference TransientReferenceDependentParam transientDependent) {
        DependentDestructionParamRecorder.recordObserverParam(transientDependent.id());
    }

    @PreDestroy
    void preDestroy() {
        ReceiverInvocationRecorder.observerReceiverDestroyed();
    }
}
