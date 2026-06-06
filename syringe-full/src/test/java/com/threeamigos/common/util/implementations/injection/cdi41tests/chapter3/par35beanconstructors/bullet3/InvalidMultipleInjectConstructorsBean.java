package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet3;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InvalidMultipleInjectConstructorsBean {

    @Inject
    InvalidMultipleInjectConstructorsBean() {
    }

    @Inject
    InvalidMultipleInjectConstructorsBean(ConstructorDependency dependency) {
    }

    @Dependent
    public static class ConstructorDependency {
    }
}
