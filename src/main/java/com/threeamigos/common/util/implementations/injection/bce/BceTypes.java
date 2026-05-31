package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.ArrayType;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.VoidType;
import jakarta.enterprise.lang.model.types.WildcardType;

import java.lang.reflect.GenericArrayType;

final class BceTypes implements Types {

    @Override
    public Type of(Class<?> clazz) {
        return BceMetadata.type(clazz);
    }

    @Override
    public VoidType ofVoid() {
        return BceMetadata.type(void.class).asVoid();
    }

    @Override
    public PrimitiveType ofPrimitive(PrimitiveType.PrimitiveKind primitiveKind) {
        switch (primitiveKind) {
            case BOOLEAN:
                return BceMetadata.type(boolean.class).asPrimitive();
            case BYTE:
                return BceMetadata.type(byte.class).asPrimitive();
            case SHORT:
                return BceMetadata.type(short.class).asPrimitive();
            case INT:
                return BceMetadata.type(int.class).asPrimitive();
            case LONG:
                return BceMetadata.type(long.class).asPrimitive();
            case FLOAT:
                return BceMetadata.type(float.class).asPrimitive();
            case DOUBLE:
                return BceMetadata.type(double.class).asPrimitive();
            case CHAR:
                return BceMetadata.type(char.class).asPrimitive();
            default:
                throw new IllegalArgumentException("Unsupported primitive kind: " + primitiveKind);
        }
    }

    @Override
    public ClassType ofClass(String className) {
        try {
            return BceMetadata.type(resolveClass(className)).asClass();
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot resolve class " + className, e);
        }
    }

    @Override
    public ClassType ofClass(ClassInfo classInfo) {
        return BceMetadata.type(BceMetadata.unwrapClassInfo(classInfo)).asClass();
    }

    @Override
    public ArrayType ofArray(Type componentType, int dimensions) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("Array dimensions must be >= 1");
        }
        Class<?> arrayClass = BceMetadata.unwrapType(componentType);
        for (int i = 0; i < dimensions; i++) {
            arrayClass = java.lang.reflect.Array.newInstance(arrayClass, 0).getClass();
        }
        return BceMetadata.type(arrayClass).asArray();
    }

    @Override
    public ParameterizedType parameterized(Class<?> genericClass, Class<?>... typeArguments) {
        java.lang.reflect.Type[] reflectTypeArguments = new java.lang.reflect.Type[typeArguments.length];
        System.arraycopy(typeArguments, 0, reflectTypeArguments, 0, typeArguments.length);
        return parameterizedType(genericClass, reflectTypeArguments);
    }

    @Override
    public ParameterizedType parameterized(Class<?> genericClass, Type... typeArguments) {
        java.lang.reflect.Type[] reflectTypeArguments = toReflectTypes(typeArguments);
        return parameterizedType(genericClass, reflectTypeArguments);
    }

    @Override
    public ParameterizedType parameterized(ClassType genericClass, Type... typeArguments) {
        Class<?> rawClass = BceMetadata.unwrapClassInfo(genericClass.declaration());
        java.lang.reflect.Type[] reflectTypeArguments = toReflectTypes(typeArguments);
        return parameterizedType(rawClass, reflectTypeArguments);
    }

    @Override
    public WildcardType wildcardWithUpperBound(Type upperBound) {
        java.lang.reflect.WildcardType wildcard = new ReflectWildcardType(
            new java.lang.reflect.Type[]{toReflectType(upperBound)},
            new java.lang.reflect.Type[0]
        );
        return BceMetadata.type(wildcard).asWildcardType();
    }

    @Override
    public WildcardType wildcardWithLowerBound(Type lowerBound) {
        java.lang.reflect.WildcardType wildcard = new ReflectWildcardType(
            new java.lang.reflect.Type[]{Object.class},
            new java.lang.reflect.Type[]{toReflectType(lowerBound)}
        );
        return BceMetadata.type(wildcard).asWildcardType();
    }

    @Override
    public WildcardType wildcardUnbounded() {
        java.lang.reflect.WildcardType wildcard = new ReflectWildcardType(
            new java.lang.reflect.Type[]{Object.class},
            new java.lang.reflect.Type[0]
        );
        return BceMetadata.type(wildcard).asWildcardType();
    }

    private ParameterizedType parameterizedType(Class<?> rawType, java.lang.reflect.Type[] typeArguments) {
        java.lang.reflect.ParameterizedType reflectionType = new ReflectParameterizedType(
            rawType,
            typeArguments,
            rawType.getDeclaringClass()
        );
        return BceMetadata.type(reflectionType).asParameterizedType();
    }

    private java.lang.reflect.Type[] toReflectTypes(Type[] typeArguments) {
        java.lang.reflect.Type[] reflectTypeArguments = new java.lang.reflect.Type[typeArguments.length];
        for (int i = 0; i < typeArguments.length; i++) {
            reflectTypeArguments[i] = toReflectType(typeArguments[i]);
        }
        return reflectTypeArguments;
    }

    private java.lang.reflect.Type toReflectType(Type modelType) {
        if (modelType == null) {
            return Object.class;
        }
        if (modelType.isWildcardType()) {
            WildcardType wildcard = modelType.asWildcardType();
            java.lang.reflect.Type upperBound = wildcard.upperBound() != null
                ? toReflectType(wildcard.upperBound()) : Object.class;
            java.lang.reflect.Type[] lowerBounds = wildcard.lowerBound() != null
                ? new java.lang.reflect.Type[]{toReflectType(wildcard.lowerBound())}
                : new java.lang.reflect.Type[0];
            return new ReflectWildcardType(new java.lang.reflect.Type[]{upperBound}, lowerBounds);
        }
        if (modelType.isArray()) {
            return new ReflectGenericArrayType(toReflectType(modelType.asArray().componentType()));
        }
        try {
            return BceMetadata.unwrapType(modelType);
        } catch (IllegalArgumentException e) {
            return Object.class;
        }
    }

    private Class<?> resolveClass(String className) throws ClassNotFoundException {
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            try {
                return Class.forName(className, false, ccl);
            } catch (ClassNotFoundException ignored) {
                // Fall through to the container/module class loader.
            }
        }

        ClassLoader fallback = BceTypes.class.getClassLoader();
        if (fallback != null && fallback != ccl) {
            return Class.forName(className, false, fallback);
        }
        return Class.forName(className);
    }

    private static final class ReflectParameterizedType implements java.lang.reflect.ParameterizedType {
        private final Class<?> rawType;
        private final java.lang.reflect.Type[] actualTypeArguments;
        private final java.lang.reflect.Type ownerType;

        private ReflectParameterizedType(Class<?> rawType,
                                         java.lang.reflect.Type[] actualTypeArguments,
                                         java.lang.reflect.Type ownerType) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments.clone();
            this.ownerType = ownerType;
        }

        @Override
        public @Nonnull java.lang.reflect.Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public @Nonnull java.lang.reflect.Type getRawType() {
            return rawType;
        }

        @Override
        public @Nonnull java.lang.reflect.Type getOwnerType() {
            return ownerType;
        }
    }

    private static final class ReflectWildcardType implements java.lang.reflect.WildcardType {
        private final java.lang.reflect.Type[] upperBounds;
        private final java.lang.reflect.Type[] lowerBounds;

        private ReflectWildcardType(java.lang.reflect.Type[] upperBounds, java.lang.reflect.Type[] lowerBounds) {
            this.upperBounds = upperBounds.clone();
            this.lowerBounds = lowerBounds.clone();
        }

        @Override
        public @Nonnull java.lang.reflect.Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public @Nonnull java.lang.reflect.Type[] getLowerBounds() {
            return lowerBounds.clone();
        }
    }

    private static final class ReflectGenericArrayType implements GenericArrayType {
        private final java.lang.reflect.Type componentType;

        private ReflectGenericArrayType(java.lang.reflect.Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public @Nonnull java.lang.reflect.Type getGenericComponentType() {
            return componentType;
        }
    }
}
