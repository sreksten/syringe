package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet9;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@Dependent
public class ConfiguredProducerFactory {

    @Produces
    @ApplicationScoped
    @Named("configuredProduct")
    @ProducerMarker
    @ProducerMethodStereotype
    ConfiguredProducedType produceConfiguredProduct() {
        return null;
    }
}
