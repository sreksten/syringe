package com.threeamigos.common.util.implementations.injection.types;

import java.lang.reflect.*;

/**
 * Utility class for extracting raw types from Java type information.
 */
public class RawTypeExtractor {

    public static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            return Array.newInstance(getRawType(componentType), 0).getClass();
        }
        if (type instanceof TypeVariable) {
            // Usually, we take the first bound (e.g., <T extends Number> -> Number)
            return getRawType(((TypeVariable<?>) type).getBounds()[0]);
        }
        if (type instanceof WildcardType) {
            // Usually, we take the upper bound (e.g., <? extends Number> -> Number)
            return getRawType(((WildcardType) type).getUpperBounds()[0]);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
