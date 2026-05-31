package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par551;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class NoInjectConstructorBean {

    @Inject
    private NoArgConstructorDependency fieldDependency;

    private final boolean usedNoArgConstructor;
    private final boolean usedArgConstructor;

    public NoInjectConstructorBean() {
        this.usedNoArgConstructor = true;
        this.usedArgConstructor = false;
    }

    public NoInjectConstructorBean(NoArgConstructorDependency ignored) {
        this.usedNoArgConstructor = false;
        this.usedArgConstructor = true;
    }

    public NoArgConstructorDependency getFieldDependency() {
        return fieldDependency;
    }

    public boolean isUsedNoArgConstructor() {
        return usedNoArgConstructor;
    }

    public boolean isUsedArgConstructor() {
        return usedArgConstructor;
    }
}
