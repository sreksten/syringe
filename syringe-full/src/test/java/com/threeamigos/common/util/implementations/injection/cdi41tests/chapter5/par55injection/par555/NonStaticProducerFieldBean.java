package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par555;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.UUID;

@ApplicationScoped
public class NonStaticProducerFieldBean {

    private static int constructedInstances;
    private static String lastConstructedInstanceId;

    private final String instanceId = UUID.randomUUID().toString();

    @Produces
    private final NonStaticProducedFieldPayload payload = new NonStaticProducedFieldPayload(instanceId);

    public NonStaticProducerFieldBean() {
        constructedInstances++;
        lastConstructedInstanceId = instanceId;
    }

    public static void reset() {
        constructedInstances = 0;
        lastConstructedInstanceId = null;
    }

    public static int getConstructedInstances() {
        return constructedInstances;
    }

    public static String getLastConstructedInstanceId() {
        return lastConstructedInstanceId;
    }
}
