package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;

public class InvalidDisposerBeanMetadataParameterBean {

    @Produces
    public String produceValue() {
        return "value";
    }

    public void disposeValue(@Disposes String value, Bean<String> metadata) {
    }
}
