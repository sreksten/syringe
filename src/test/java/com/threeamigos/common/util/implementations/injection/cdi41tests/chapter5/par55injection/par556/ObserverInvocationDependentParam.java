package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

import java.util.UUID;

@Dependent
public class ObserverInvocationDependentParam {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    @PreDestroy
    void preDestroy() {
        ObserverInvocationRecorder.record("observer-dependent-pre:" + id);
    }
}
