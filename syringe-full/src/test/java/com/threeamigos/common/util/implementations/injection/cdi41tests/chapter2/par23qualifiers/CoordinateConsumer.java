package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import jakarta.inject.Inject;

public class CoordinateConsumer {

    @Inject
    @Location("north")
    @Location("south")
    private Coordinate coordinate;

    public Coordinate getCoordinate() {
        return coordinate;
    }
}
