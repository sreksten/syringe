package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par39namedatinjectionpoints.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
public class InvalidEmptyNamedConstructorParameterBean {

    private final ConstructorDependency dependency;

    @Inject
    public InvalidEmptyNamedConstructorParameterBean(@Named ConstructorDependency dependency) {
        this.dependency = dependency;
    }

    public ConstructorDependency getDependency() {
        return dependency;
    }

    @Dependent
    @Named("constructorDependency")
    public static class ConstructorDependency {
    }
}
