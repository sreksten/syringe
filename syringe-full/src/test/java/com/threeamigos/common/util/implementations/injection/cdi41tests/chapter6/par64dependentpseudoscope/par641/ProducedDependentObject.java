package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641;

public class ProducedDependentObject {

    private final String producerParamId;

    public ProducedDependentObject(String producerParamId) {
        this.producerParamId = producerParamId;
    }

    public String producerParamId() {
        return producerParamId;
    }
}
