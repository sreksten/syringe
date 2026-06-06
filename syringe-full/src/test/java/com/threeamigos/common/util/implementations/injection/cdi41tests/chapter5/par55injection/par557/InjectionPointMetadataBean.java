package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@Dependent
public class InjectionPointMetadataBean {

    @Inject
    private InjectionPoint fieldInjectionPoint;

    @Inject
    private transient InjectionPoint transientFieldInjectionPoint;

    private final InjectionPoint constructorInjectionPoint;

    private InjectionPoint initializerInjectionPoint;

    @Inject
    InjectionPointMetadataBean(InjectionPoint constructorInjectionPoint) {
        this.constructorInjectionPoint = constructorInjectionPoint;
    }

    @Inject
    void initialize(InjectionPoint initializerInjectionPoint) {
        this.initializerInjectionPoint = initializerInjectionPoint;
    }

    public InjectionPoint getFieldInjectionPoint() {
        return fieldInjectionPoint;
    }

    public InjectionPoint getTransientFieldInjectionPoint() {
        return transientFieldInjectionPoint;
    }

    public InjectionPoint getConstructorInjectionPoint() {
        return constructorInjectionPoint;
    }

    public InjectionPoint getInitializerInjectionPoint() {
        return initializerInjectionPoint;
    }
}
