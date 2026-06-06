package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.util.AnnotationLiteral;

public final class DynamicRedLiteral extends AnnotationLiteral<DynamicRed> implements DynamicRed {

    public static final DynamicRedLiteral INSTANCE = new DynamicRedLiteral();

    private DynamicRedLiteral() {
    }
}
