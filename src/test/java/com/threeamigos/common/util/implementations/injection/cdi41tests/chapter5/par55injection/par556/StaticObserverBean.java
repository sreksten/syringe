package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;

@Dependent
public class StaticObserverBean {

    private static int constructedInstances;

    public StaticObserverBean() {
        constructedInstances++;
    }

    public static void reset() {
        constructedInstances = 0;
    }

    public static int getConstructedInstances() {
        return constructedInstances;
    }

    static void onEvent(@Observes StaticObserverInvocationEvent event, ObserverInvocationDependentParam dependency) {
        ObserverInvocationRecorder.record("static-observer:" + event.getId() + ":" + dependency.getId());
    }
}
