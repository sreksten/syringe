package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilderFactory;
import jakarta.enterprise.inject.build.compatible.spi.BuildServices;

/**
 * Minimal BuildServices implementation for BCE phase execution.
 */
public class BceBuildServices implements BuildServices {

    private final AnnotationBuilderFactory annotationBuilderFactory = new BceAnnotationBuilderFactory();

    @Override
    public AnnotationBuilderFactory annotationBuilderFactory() {
        return annotationBuilderFactory;
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
