package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;

@RequestScoped
public class ConditionalRequestScopedObserverBean {

    private static int constructedInstances;
    private static int observedEvents;

    public ConditionalRequestScopedObserverBean() {
        constructedInstances++;
    }

    public static void reset() {
        constructedInstances = 0;
        observedEvents = 0;
    }

    public static int getConstructedInstances() {
        return constructedInstances;
    }

    public static int getObservedEvents() {
        return observedEvents;
    }

    void onEvent(@Observes(notifyObserver = Reception.IF_EXISTS) ConditionalObserverInvocationEvent event) {
        observedEvents++;
        ObserverInvocationRecorder.record("conditional-observer:" + event.getId());
    }
}
