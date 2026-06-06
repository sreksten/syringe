package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet5;

public class ProducedByField {

    private final String source;

    public ProducedByField(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
