package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InvalidStaticInjectedFieldBean {

    @Inject
    static StaticFieldDependency dependency;

    @Dependent
    public static class StaticFieldDependency {
    }
}
