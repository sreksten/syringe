package com.threeamigos.common.util.implementations.injection.util;

import jakarta.annotation.Nonnull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

public final class SimpleParameterizedType implements ParameterizedType {
    private final Class<?> rawType;
    private final Type[] actualTypeArguments;
    private final Type ownerType;

    public SimpleParameterizedType(Class<?> rawType, Type[] actualTypeArguments, Type ownerType) {
        this.rawType = rawType;
        this.actualTypeArguments = actualTypeArguments.clone();
        this.ownerType = ownerType;
    }

    @Override
    public @Nonnull Type[] getActualTypeArguments() {
        return actualTypeArguments.clone();
    }

    @Override
    public @Nonnull Type getRawType() {
        return rawType;
    }

    @Override
    public Type getOwnerType() {
        return ownerType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType that = (ParameterizedType) o;
        return Objects.equals(rawType, that.getRawType())
                && Objects.equals(ownerType, that.getOwnerType())
                && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(actualTypeArguments)
                ^ Objects.hashCode(ownerType)
                ^ Objects.hashCode(rawType);
    }
}
