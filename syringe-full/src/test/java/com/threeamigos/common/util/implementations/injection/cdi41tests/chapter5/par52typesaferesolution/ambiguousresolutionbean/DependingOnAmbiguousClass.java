package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguousresolutionbean;

import jakarta.inject.Inject;

public class DependingOnAmbiguousClass {

    @Inject
    private AmbiguousInterface ambiguousInterface;
}
