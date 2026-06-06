package com.threeamigos.common.util.implementations.injection.resolution;

import jakarta.annotation.Nonnull;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves generic type variables declared on superclasses to the concrete type arguments
 * of a concrete bean class.
 */
public final class GenericTypeResolver {

    private GenericTypeResolver() {
    }

    public static Type resolve(Type declaredType, Class<?> concreteBeanClass, Class<?> declaringClass) {
        if (declaredType == null || concreteBeanClass == null || declaringClass == null) {
            return declaredType;
        }

        if (!declaringClass.isAssignableFrom(concreteBeanClass)) {
            return declaredType;
        }

        Map<TypeVariable<?>, Type> typeMapping = buildTypeMapping(concreteBeanClass, declaringClass);
        return resolveType(declaredType, typeMapping);
    }

    private static Map<TypeVariable<?>, Type> buildTypeMapping(Class<?> concreteBeanClass, Class<?> declaringClass) {
        Map<TypeVariable<?>, Type> mapping = new HashMap<>();

        Class<?> current = concreteBeanClass;
        while (current != null && !Object.class.equals(current) && !declaringClass.equals(current)) {
            Type genericSuperclass = current.getGenericSuperclass();
            if (!(genericSuperclass instanceof ParameterizedType)) {
                current = current.getSuperclass();
                continue;
            }

            ParameterizedType parameterizedSuperclass = (ParameterizedType) genericSuperclass;
            Type raw = parameterizedSuperclass.getRawType();
            if (!(raw instanceof Class)) {
                current = current.getSuperclass();
                continue;
            }

            Class<?> rawSuperclass = (Class<?>) raw;
            TypeVariable<?>[] variables = rawSuperclass.getTypeParameters();
            Type[] actualArguments = parameterizedSuperclass.getActualTypeArguments();

            for (int i = 0; i < variables.length; i++) {
                Type resolvedArgument = resolveType(actualArguments[i], mapping);
                mapping.put(variables[i], resolvedArgument);
            }

            current = rawSuperclass;
        }

        return mapping;
    }

    private static Type resolveType(Type type, Map<TypeVariable<?>, Type> mapping) {
        if (type instanceof TypeVariable) {
            TypeVariable<?> variable = (TypeVariable<?>) type;
            Type resolved = mapping.get(variable);
            if (resolved == null || resolved.equals(variable)) {
                return type;
            }
            return resolveType(resolved, mapping);
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] originalArguments = parameterizedType.getActualTypeArguments();
            Type[] resolvedArguments = new Type[originalArguments.length];
            boolean changed = false;

            for (int i = 0; i < originalArguments.length; i++) {
                resolvedArguments[i] = resolveType(originalArguments[i], mapping);
                if (!resolvedArguments[i].equals(originalArguments[i])) {
                    changed = true;
                }
            }

            Type ownerType = parameterizedType.getOwnerType();
            Type resolvedOwnerType = ownerType == null ? null : resolveType(ownerType, mapping);
            if (resolvedOwnerType != null && !resolvedOwnerType.equals(ownerType)) {
                changed = true;
            }

            if (!changed) {
                return type;
            }

            return new ResolvedParameterizedType(
                    (Class<?>) parameterizedType.getRawType(),
                    resolvedArguments,
                    resolvedOwnerType
            );
        }

        if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) type;
            Type originalComponent = genericArrayType.getGenericComponentType();
            Type resolvedComponent = resolveType(originalComponent, mapping);

            if (resolvedComponent.equals(originalComponent)) {
                return type;
            }

            return new ResolvedGenericArrayType(resolvedComponent);
        }

        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] lowerBounds = wildcardType.getLowerBounds();
            Type[] upperBounds = wildcardType.getUpperBounds();

            boolean changed = false;
            Type[] resolvedLower = new Type[lowerBounds.length];
            Type[] resolvedUpper = new Type[upperBounds.length];

            for (int i = 0; i < lowerBounds.length; i++) {
                resolvedLower[i] = resolveType(lowerBounds[i], mapping);
                if (!resolvedLower[i].equals(lowerBounds[i])) {
                    changed = true;
                }
            }

            for (int i = 0; i < upperBounds.length; i++) {
                resolvedUpper[i] = resolveType(upperBounds[i], mapping);
                if (!resolvedUpper[i].equals(upperBounds[i])) {
                    changed = true;
                }
            }

            if (!changed) {
                return type;
            }

            return new ResolvedWildcardType(resolvedUpper, resolvedLower);
        }

        return type;
    }

    private static final class ResolvedParameterizedType implements ParameterizedType {

        private final Class<?> rawType;
        private final Type[] actualTypeArguments;
        private final Type ownerType;

        private ResolvedParameterizedType(Class<?> rawType, Type[] actualTypeArguments, Type ownerType) {
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
        public String getTypeName() {
            StringBuilder builder = new StringBuilder(rawType.getTypeName());
            if (actualTypeArguments.length > 0) {
                builder.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(actualTypeArguments[i].getTypeName());
                }
                builder.append(">");
            }
            return builder.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ParameterizedType)) {
                return false;
            }
            ParameterizedType that = (ParameterizedType) other;
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

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private static final class ResolvedGenericArrayType implements GenericArrayType {

        private final Type componentType;

        private ResolvedGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public @Nonnull Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public String getTypeName() {
            return componentType.getTypeName() + "[]";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GenericArrayType)) {
                return false;
            }
            GenericArrayType that = (GenericArrayType) other;
            return Objects.equals(componentType, that.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentType);
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private static final class ResolvedWildcardType implements WildcardType {

        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        private ResolvedWildcardType(Type[] upperBounds, Type[] lowerBounds) {
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
        public String getTypeName() {
            if (lowerBounds.length > 0) {
                return "? super " + lowerBounds[0].getTypeName();
            }

            if (upperBounds.length == 0 || Object.class.equals(upperBounds[0])) {
                return "?";
            }

            return "? extends " + upperBounds[0].getTypeName();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WildcardType)) {
                return false;
            }
            WildcardType that = (WildcardType) other;
            return Arrays.equals(upperBounds, that.getUpperBounds())
                    && Arrays.equals(lowerBounds, that.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }
}
