package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par63normalscopespseudoscopes;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class ApplicationScopedLifecycleBean {
    private static final AtomicInteger PRE_DESTROY_CALLS = new AtomicInteger(0);

    static void reset() {
        PRE_DESTROY_CALLS.set(0);
    }

    static int preDestroyCalls() {
        return PRE_DESTROY_CALLS.get();
    }

    @PreDestroy
    void preDestroy() {
        PRE_DESTROY_CALLS.incrementAndGet();
    }
}
