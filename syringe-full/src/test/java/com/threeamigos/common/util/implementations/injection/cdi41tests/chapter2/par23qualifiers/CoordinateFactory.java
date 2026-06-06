package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.enterprise.inject.Produces;

import java.lang.reflect.Method;

public class CoordinateFactory {

    private static final String[] PRODUCER_COORDINATES = resolveProducerCoordinates();

    @Produces
    @Location("north")
    @Location("south")
    public Coordinate createCoordinate() {
        return new Coordinate(PRODUCER_COORDINATES);
    }

    private static String[] resolveProducerCoordinates() {
        try {
            Method producerMethod = CoordinateFactory.class.getDeclaredMethod("createCoordinate");
            return QualifiersHelper.extractLocationValues(producerMethod.getAnnotations());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Could not introspect coordinate producer method", e);
        }
    }

}
