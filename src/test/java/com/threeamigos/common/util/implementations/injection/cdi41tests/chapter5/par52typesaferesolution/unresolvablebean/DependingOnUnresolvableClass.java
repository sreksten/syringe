package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.unresolvablebean;

import jakarta.inject.Inject;

public class DependingOnUnresolvableClass {

    @Inject
    private UnresolvableInterface unresolvableInterface;
}
