package com.threeamigos.common.util.implementations.injection.types;

import java.lang.reflect.*;

public class TypesHelper {

    private TypesHelper() {}

    /**
     * Checks if the given types array contains a type variable.
     * @param types the types' array to check
     * @return true if the types' array contains a type variable, false otherwise
     */
    public static boolean containsTypeVariable(Type[] types) {
        for (Type type : types) {
            if (containsTypeVariable(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given type contains a type variable.
     * @param type the type to check
     * @return true if the type contains a type variable, false otherwise
     */
    public static boolean containsTypeVariable(Type type) {
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            return containsTypeVariable(((ParameterizedType) type).getActualTypeArguments());
        }
        if (type instanceof GenericArrayType) {
            return containsTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof Class && ((Class<?>) type).isArray()) {
            return containsTypeVariable(((Class<?>) type).getComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            return containsTypeVariable(wildcardType.getLowerBounds()) ||
                    containsTypeVariable(wildcardType.getUpperBounds());
        }
        return false;
    }

    /**
     * Normalizes the resolved type by replacing generic array types with their corresponding array classes.
     * @param resolvedType the resolved type to normalize
     * @return the normalized type
     */
    public static Type normalizeResolvedType(Type resolvedType) {
        if (!(resolvedType instanceof GenericArrayType)) {
            return resolvedType;
        }

        Type component = ((GenericArrayType) resolvedType).getGenericComponentType();
        if (component instanceof Class<?>) {
            return Array.newInstance((Class<?>) component, 0).getClass();
        }
        return resolvedType;
    }


}
