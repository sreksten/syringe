package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FieldProducerConsumer {

    @Inject
    private FieldProducedObject dependency;

    public FieldProducedObject getDependency() {
        return dependency;
    }
}
