package com.threeamigos.common.util.implementations.injection.util;

import jakarta.annotation.Nonnull;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Objects;

public final class SimpleGenericArrayType implements GenericArrayType {
    private final Type componentType;

    public SimpleGenericArrayType(Type componentType) {
        this.componentType = componentType;
    }

    @Override
    public @Nonnull Type getGenericComponentType() {
        return componentType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GenericArrayType)) {
            return false;
        }
        GenericArrayType that = (GenericArrayType) o;
        return Objects.equals(componentType, that.getGenericComponentType());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(componentType);
    }
}
