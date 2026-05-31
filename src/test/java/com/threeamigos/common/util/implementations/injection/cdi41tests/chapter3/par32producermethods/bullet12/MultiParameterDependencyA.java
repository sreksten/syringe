package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12;

import jakarta.enterprise.context.Dependent;

@Dependent
public class MultiParameterDependencyA {

    String code() {
        return "A";
    }
}
