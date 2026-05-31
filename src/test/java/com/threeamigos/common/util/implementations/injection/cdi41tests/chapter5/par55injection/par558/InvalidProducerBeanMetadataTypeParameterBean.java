package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;

public class InvalidProducerBeanMetadataTypeParameterBean {

    @Produces
    public Integer produceInvalid(Bean<String> bean) {
        return 42;
    }
}
