package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

import java.util.UUID;

@Dependent
public class TransientReferenceDependentParam {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }

    @PreDestroy
    void preDestroy() {
        DependentDestructionParamRecorder.recordPreDestroy(id);
    }
}
