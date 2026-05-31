package com.threeamigos.common.util.implementations.injection.annotations;

import jakarta.enterprise.inject.Default;
import java.lang.annotation.Annotation;
import java.io.Serializable;

/**
 * Helper class to create instances of @Default.
 */
@SuppressWarnings("all")
public class DefaultLiteral implements Default, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public Class<? extends Annotation> annotationType() {
        return Default.class;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Default;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
