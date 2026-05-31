package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

import java.util.UUID;

@Dependent
public class TrackedDependentObject {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }

    @PreDestroy
    void preDestroy() {
        DependentObjectsRecorder.recordPreDestroy(id);
    }
}
