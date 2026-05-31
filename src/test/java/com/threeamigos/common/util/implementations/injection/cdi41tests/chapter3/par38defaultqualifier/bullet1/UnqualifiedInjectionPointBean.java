package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par38defaultqualifier.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class UnqualifiedInjectionPointBean {

    @Inject
    FieldDependency dependency;

    public FieldDependency getDependency() {
        return dependency;
    }

    @Dependent
    public static class FieldDependency {
    }
}
