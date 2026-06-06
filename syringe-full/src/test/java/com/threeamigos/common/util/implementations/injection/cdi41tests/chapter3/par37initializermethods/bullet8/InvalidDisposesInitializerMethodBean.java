package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Inject;

@Dependent
public class InvalidDisposesInitializerMethodBean {

    @Inject
    void initialize(@Disposes String value) {
    }
}
