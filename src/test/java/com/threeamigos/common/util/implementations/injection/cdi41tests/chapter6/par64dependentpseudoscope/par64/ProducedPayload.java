package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

public class ProducedPayload {

    private final String source;

    public ProducedPayload(String source) {
        this.source = source;
    }

    public String source() {
        return source;
    }
}
