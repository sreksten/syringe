package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

@Dependent
public class DependentDestroyableBean {

    private static int preDestroyCalls;

    public static void reset() {
        preDestroyCalls = 0;
    }

    public static int getPreDestroyCalls() {
        return preDestroyCalls;
    }

    @PreDestroy
    void preDestroy() {
        preDestroyCalls++;
    }
}
