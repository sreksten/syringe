package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilderFactory;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

final class BceAnnotationBuilderFactory implements AnnotationBuilderFactory {

    @Override
    public AnnotationBuilder create(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("annotationType cannot be null");
        }
        return (AnnotationBuilder) Proxy.newProxyInstance(
            AnnotationBuilder.class.getClassLoader(),
            new Class<?>[]{AnnotationBuilder.class},
            new AnnotationBuilderHandler(annotationType)
        );
    }

    @Override
    public AnnotationBuilder create(ClassInfo annotationType) {
        Class<?> runtimeClass = BceMetadata.unwrapClassInfo(annotationType);
        if (!runtimeClass.isAnnotation()) {
            throw new IllegalArgumentException("ClassInfo does not represent annotation type: " + runtimeClass.getName());
        }
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) runtimeClass;
        return create(annotationClass);
    }

    private static class AnnotationBuilderHandler implements InvocationHandler {
        private final Class<? extends Annotation> annotationType;
        private final Map<String, Object> members = new LinkedHashMap<>();

        private AnnotationBuilderHandler(Class<? extends Annotation> annotationType) {
            this.annotationType = annotationType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("build".equals(methodName)) {
                return BceMetadata.annotationInfo(BceCommonFunctions.buildAnnotationProxy(annotationType, members));
            }
            if ("member".equals(methodName) && args != null) {
                if (args.length == 2) {
                    members.put((String) args[0], normalizeValue(args[1]));
                    return proxy;
                }
                if (args.length == 3 && args[1] instanceof Class && args[2] instanceof String) {
                    Object enumValue = evaluateEnumFromClass(args[1], args[2]);
                    members.put((String) args[0], normalizeValue(enumValue));
                    return proxy;
                }
                if (args.length == 3 && args[1] instanceof ClassInfo && args[2] instanceof String) {
                    Object enumValue = evaluateEnumFromClassInfo(args[1], args[2]);
                    members.put((String) args[0], normalizeValue(enumValue));
                    return proxy;
                }
            }
            if ("value".equals(methodName) && args != null && args.length >= 1) {
                Object value;
                if (args.length == 1) {
                    value = args[0];
                } else if (args.length == 2 && args[0] instanceof Class && args[1] instanceof String) {
                    value = evaluateEnumFromClass(args[0], args[1]);
                } else if (args.length == 2 && args[0] instanceof ClassInfo && args[1] instanceof String) {
                    value = evaluateEnumFromClassInfo(args[0], args[1]);
                } else {
                    value = args[0];
                }
                members.put("value", normalizeValue(value));
                return proxy;
            }
            switch (methodName) {
                case "toString":
                    return "AnnotationBuilder(" + annotationType.getName() + ")";
                case "hashCode":
                    return members.hashCode();
                case "equals":
                    return proxy == args[0];
            }
            return proxy;
        }

        private Object evaluateEnumFromClassInfo(Object enumClassInfoAsObject, Object enumValue) {
            Class<?> enumClass = BceMetadata.unwrapClassInfo((ClassInfo) enumClassInfoAsObject);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object object = Enum.valueOf((Class<? extends Enum>)enumClass, (String) enumValue);
            return object;
        }

        private Object evaluateEnumFromClass(Object enumClassAsObject, Object enumValue) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> enumClass = (Class<? extends Enum>) enumClassAsObject;
            @SuppressWarnings("unchecked")
            Object object = Enum.valueOf(enumClass, (String) enumValue);
            return object;
        }

        private Object normalizeValue(Object value) {
            if (value instanceof ClassInfo) {
                return BceMetadata.unwrapClassInfo((ClassInfo) value);
            }
            if (value instanceof ClassInfo[]) {
                return BceMetadata.unwrapClassInfo((ClassInfo[]) value);
            }
            if (value instanceof Type) {
                return BceMetadata.unwrapType((Type) value);
            }
            if (value instanceof Type[]) {
                return BceMetadata.unwrapTypes((Type[]) value);
            }
            if (value instanceof AnnotationInfo) {
                return BceMetadata.unwrapAnnotationInfo((AnnotationInfo) value);
            }
            if (value instanceof AnnotationInfo[]) {
                return BceMetadata.unwrapAnnotationInfo((AnnotationInfo[]) value);
            }
            return value;
        }
    }
}
