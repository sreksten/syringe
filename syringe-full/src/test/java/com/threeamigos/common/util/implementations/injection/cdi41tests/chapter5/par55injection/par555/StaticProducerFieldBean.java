package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par555;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class StaticProducerFieldBean {

    private static int constructedInstances;

    @Produces
    private static final StaticProducedFieldPayload payload = new StaticProducedFieldPayload("static-field");

    public StaticProducerFieldBean() {
        constructedInstances++;
    }

    public static void reset() {
        constructedInstances = 0;
    }

    public static int getConstructedInstances() {
        return constructedInstances;
    }
}
