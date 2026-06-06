package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MethodProducerConsumer {

    @Inject
    private MethodProducedObject dependency;

    public MethodProducedObject getDependency() {
        return dependency;
    }
}
