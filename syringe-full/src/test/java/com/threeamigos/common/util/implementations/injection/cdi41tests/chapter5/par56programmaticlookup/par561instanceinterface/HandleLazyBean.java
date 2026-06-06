package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

@Dependent
public class HandleLazyBean {

    private static int createdCount;
    private static int preDestroyCount;

    public HandleLazyBean() {
        createdCount++;
    }

    public static void reset() {
        createdCount = 0;
        preDestroyCount = 0;
    }

    public static int getCreatedCount() {
        return createdCount;
    }

    public static int getPreDestroyCount() {
        return preDestroyCount;
    }

    @PreDestroy
    void preDestroy() {
        preDestroyCount++;
    }

    public String value() {
        return "handle-lazy";
    }
}
