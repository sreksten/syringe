package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet3;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ValidInjectedFieldBean {

    @Inject
    ValidFieldDependency dependency;

    public ValidFieldDependency getDependency() {
        return dependency;
    }

    @Dependent
    public static class ValidFieldDependency {
    }
}
