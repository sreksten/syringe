package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("Order")
public class OrderProcessorBeanMetadataConsumer {

    @Inject
    private Bean<OrderProcessorBeanMetadataConsumer> bean;

    public String getInjectedBeanName() {
        return bean.getName();
    }

    public Class<?> getInjectedBeanClass() {
        return bean.getBeanClass();
    }
}
