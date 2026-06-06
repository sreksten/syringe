package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

public class SimpleDependentEvent {

    private final String id;

    public SimpleDependentEvent(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
