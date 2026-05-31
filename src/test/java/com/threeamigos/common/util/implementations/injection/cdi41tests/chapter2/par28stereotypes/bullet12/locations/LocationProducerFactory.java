package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.locations;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class LocationProducerFactory {

    @Produces
    @LocationAction
    public LocationMethodProduct createMethodProduct() {
        return new LocationMethodProduct();
    }

    @Produces
    @LocationAction
    private LocationFieldProduct fieldProduct = new LocationFieldProduct();
}
