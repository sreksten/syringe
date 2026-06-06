package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet5;

import jakarta.enterprise.event.Observes;

import java.util.concurrent.atomic.AtomicInteger;

public class NonBeanObserverHolder {

    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger(0);

    void onPingEvent(@Observes PingEvent event) {
        INVOCATION_COUNT.incrementAndGet();
    }

    public static void reset() {
        INVOCATION_COUNT.set(0);
    }

    public static int getInvocationCount() {
        return INVOCATION_COUNT.get();
    }
}
