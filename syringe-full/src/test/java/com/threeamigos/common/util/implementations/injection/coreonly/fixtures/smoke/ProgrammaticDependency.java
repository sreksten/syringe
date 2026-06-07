package com.threeamigos.common.util.implementations.injection.coreonly.fixtures.smoke;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

import java.util.concurrent.atomic.AtomicInteger;

@Dependent
public class ProgrammaticDependency {

    private static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger();

    public static void resetCounters() {
        PRE_DESTROY_COUNT.set(0);
    }

    public static int getPreDestroyCount() {
        return PRE_DESTROY_COUNT.get();
    }

    @PreDestroy
    void onPreDestroy() {
        PRE_DESTROY_COUNT.incrementAndGet();
    }
}
