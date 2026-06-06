package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par551;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ConstructorInjectedBean {

    private final ConstructorDependency constructorDependency;
    private final boolean usedInjectConstructor;
    private final boolean usedNoArgConstructor;

    @Inject
    public ConstructorInjectedBean(ConstructorDependency constructorDependency) {
        this.constructorDependency = constructorDependency;
        this.usedInjectConstructor = true;
        this.usedNoArgConstructor = false;
    }

    public ConstructorInjectedBean() {
        this.constructorDependency = null;
        this.usedInjectConstructor = false;
        this.usedNoArgConstructor = true;
    }

    public ConstructorDependency getConstructorDependency() {
        return constructorDependency;
    }

    public boolean isUsedInjectConstructor() {
        return usedInjectConstructor;
    }

    public boolean isUsedNoArgConstructor() {
        return usedNoArgConstructor;
    }
}
