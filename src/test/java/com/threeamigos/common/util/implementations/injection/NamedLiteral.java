package com.threeamigos.common.util.implementations.injection;

import jakarta.annotation.Nonnull;

import jakarta.inject.Named;
import java.lang.annotation.Annotation;

/**
 * Helper class to create instances of @Named for testing.
 */
@SuppressWarnings("all")
class NamedLiteral implements Named {
    private final String value;

    public NamedLiteral(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public @Nonnull Class<? extends Annotation> annotationType() {
        return Named.class;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Named)) return false;
        Named other = (Named) o;
        return value.equals(other.value());
    }

    @Override
    public int hashCode() {
        return (127 * "value".hashCode()) ^ value.hashCode();
    }
}
