package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ObserverOwnerBean {

    void onEvent(@Observes SimpleDependentEvent event, InvocationScopedDependentBean dependent) {
        DependentInvocationRecorder.recordObserverParameter(dependent.id());
    }
}
