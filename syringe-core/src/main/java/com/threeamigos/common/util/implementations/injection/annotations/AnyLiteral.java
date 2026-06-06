package com.threeamigos.common.util.implementations.injection.annotations;

import jakarta.enterprise.inject.Any;
import java.lang.annotation.Annotation;
import java.io.Serializable;

/**
 * Helper class to create instances of @Any.
 */
@SuppressWarnings("all")
public class AnyLiteral implements Any, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public Class<? extends Annotation> annotationType() {
        return Any.class;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Any;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
