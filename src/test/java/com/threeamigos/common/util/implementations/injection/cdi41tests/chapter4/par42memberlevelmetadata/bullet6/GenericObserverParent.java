package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6;

import jakarta.enterprise.event.Observes;

public abstract class GenericObserverParent<T> {

    public static int observedCount;
    public static Object lastObserved;

    void onEvent(@Observes T event) {
        observedCount++;
        lastObserved = event;
    }

    public static void reset() {
        observedCount = 0;
        lastObserved = null;
    }
}
