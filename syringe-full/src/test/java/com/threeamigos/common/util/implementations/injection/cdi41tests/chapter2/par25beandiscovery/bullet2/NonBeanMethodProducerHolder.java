package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet2;

import jakarta.enterprise.inject.Produces;

public class NonBeanMethodProducerHolder {

    @Produces
    MethodProducedObject produce() {
        return new MethodProducedObject();
    }
}
