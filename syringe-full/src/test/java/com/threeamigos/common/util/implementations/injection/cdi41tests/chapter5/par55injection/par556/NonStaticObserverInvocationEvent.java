package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556;

public class NonStaticObserverInvocationEvent {

    private final String id;

    public NonStaticObserverInvocationEvent(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
