package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;

public class InvalidBeanMetadataTypeParameterConsumer {

    @Inject
    private Bean<String> bean;

    public Bean<String> getBean() {
        return bean;
    }
}
