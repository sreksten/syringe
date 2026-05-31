package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet4;

import jakarta.enterprise.event.Observes;

public abstract class ParentObserverBean {

    public static int observedEvents = 0;

    public void onEvent(@Observes InheritedObserverEvent event) {
        observedEvents++;
    }

    public static void reset() {
        observedEvents = 0;
    }
}
