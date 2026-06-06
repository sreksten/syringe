package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@ApplicationScoped
public class NonDependentInjectionPointBean {

    @Inject
    private InjectionPoint injectionPoint;

    public InjectionPoint getInjectionPoint() {
        return injectionPoint;
    }
}
