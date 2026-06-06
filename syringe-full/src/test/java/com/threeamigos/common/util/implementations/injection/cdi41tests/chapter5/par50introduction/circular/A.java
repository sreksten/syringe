package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par50introduction.circular;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class A {

    @Inject
    private B b;
}
