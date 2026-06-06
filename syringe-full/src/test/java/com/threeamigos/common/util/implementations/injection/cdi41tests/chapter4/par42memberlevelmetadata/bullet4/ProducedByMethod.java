package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet4;

public class ProducedByMethod {

    private final String source;

    public ProducedByMethod(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
