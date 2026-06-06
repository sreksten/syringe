package com.threeamigos.common.util.implementations.injection.util;

import jakarta.annotation.Nonnull;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

public final class SimpleWildcardType implements WildcardType {
    private final Type[] upperBounds;
    private final Type[] lowerBounds;

    public SimpleWildcardType(Type[] upperBounds, Type[] lowerBounds) {
        this.upperBounds = upperBounds.clone();
        this.lowerBounds = lowerBounds.clone();
    }

    @Override
    public @Nonnull Type[] getUpperBounds() {
        return upperBounds.clone();
    }

    @Override
    public @Nonnull Type[] getLowerBounds() {
        return lowerBounds.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WildcardType)) {
            return false;
        }
        WildcardType that = (WildcardType) o;
        return Arrays.equals(upperBounds, that.getUpperBounds())
                && Arrays.equals(lowerBounds, that.getLowerBounds());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
    }
}
