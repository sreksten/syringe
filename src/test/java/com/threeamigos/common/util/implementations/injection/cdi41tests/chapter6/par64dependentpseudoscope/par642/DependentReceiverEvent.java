package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

public class DependentReceiverEvent {

    private final String id;

    public DependentReceiverEvent(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
