package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.Intercepted;
import jakarta.inject.Inject;

public class InvalidInterceptedBeanMetadataConsumer {

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    public Bean<?> getInterceptedBean() {
        return interceptedBean;
    }
}
