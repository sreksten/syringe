package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.inject.Inject;

public class InvalidInterceptorMetadataConsumer {

    @Inject
    private Interceptor<InvalidInterceptorMetadataConsumer> interceptor;

    public Interceptor<InvalidInterceptorMetadataConsumer> getInterceptor() {
        return interceptor;
    }
}
