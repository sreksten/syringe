package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.UUID;

@ApplicationScoped
public class NonStaticObserverBean {

    private static int constructedInstances;

    private final String instanceId = UUID.randomUUID().toString();

    public NonStaticObserverBean() {
        constructedInstances++;
    }

    public static void reset() {
        constructedInstances = 0;
    }

    public static int getConstructedInstances() {
        return constructedInstances;
    }

    void onEvent(@Observes NonStaticObserverInvocationEvent event, ObserverInvocationDependentParam dependency) {
        ObserverInvocationRecorder.record("nonstatic-observer:" + instanceId + ":" + event.getId() + ":" + dependency.getId());
    }
}
