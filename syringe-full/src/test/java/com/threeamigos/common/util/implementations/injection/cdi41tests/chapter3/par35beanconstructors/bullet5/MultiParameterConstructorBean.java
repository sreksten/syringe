package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class MultiParameterConstructorBean {

    private final ConstructorDependencyA dependencyA;
    private final ConstructorDependencyB dependencyB;
    private final ConstructorDependencyC dependencyC;

    @Inject
    MultiParameterConstructorBean(ConstructorDependencyA dependencyA,
                                  ConstructorDependencyB dependencyB,
                                  ConstructorDependencyC dependencyC) {
        this.dependencyA = dependencyA;
        this.dependencyB = dependencyB;
        this.dependencyC = dependencyC;
    }

    public ConstructorDependencyA getDependencyA() {
        return dependencyA;
    }

    public ConstructorDependencyB getDependencyB() {
        return dependencyB;
    }

    public ConstructorDependencyC getDependencyC() {
        return dependencyC;
    }
}
