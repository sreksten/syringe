package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet3;

import jakarta.enterprise.inject.Produces;

public class NonBeanFieldProducerHolder {

    @Produces
    FieldProducedObject fieldProducedObject = new FieldProducedObject();
}
