package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

public class Coordinate {

    private final String[] coordinates;

    public Coordinate(String ... coordinates) {
        this.coordinates = coordinates;
    }

    public String[] getCoordinates() {
        return coordinates;
    }

}
