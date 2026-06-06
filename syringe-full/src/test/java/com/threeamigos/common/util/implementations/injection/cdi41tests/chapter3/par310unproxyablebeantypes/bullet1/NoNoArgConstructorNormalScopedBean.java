package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet1;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@ApplicationScoped
public class NoNoArgConstructorNormalScopedBean {

    @Inject
    public NoNoArgConstructorNormalScopedBean(ConstructorDependency dependency) {
    }

    @Dependent
    public static class ConstructorDependency {
    }
}
