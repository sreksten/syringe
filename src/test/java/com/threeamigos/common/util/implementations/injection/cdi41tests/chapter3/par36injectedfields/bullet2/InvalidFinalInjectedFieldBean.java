package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InvalidFinalInjectedFieldBean {

    @Inject
    final FinalFieldDependency dependency = null;

    @Dependent
    public static class FinalFieldDependency {
    }
}
