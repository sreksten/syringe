package com.threeamigos.common.util.implementations.injection.annotations;

import com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility methods to resolve member/parameter metadata from ProcessAnnotatedType overrides.
 */
public final class AnnotatedMetadataHelper {

    private AnnotatedMetadataHelper() {
    }

    public static Annotation[] annotationsOf(AnnotatedType<?> annotatedType, AnnotatedElement element) {
        Annotated annotated = annotatedOf(annotatedType, element);
        if (annotated != null) {
            return annotated.getAnnotations().toArray(new Annotation[0]);
        }
        if (annotatedType != null && isMemberElement(element)) {
            // ProcessAnnotatedType#setAnnotatedType may replace the member set entirely.
            // If a reflection member is absent from the replacement AnnotatedType, treat it
            // as non-existent for metadata purposes.
            return new Annotation[0];
        }
        return element.getAnnotations();
    }

    public static Annotated annotatedOf(AnnotatedType<?> annotatedType, AnnotatedElement element) {
        if (annotatedType == null || element == null) {
            return null;
        }
        if (element instanceof Field) {
            return findAnnotatedField(annotatedType, (Field) element);
        }
        if (element instanceof Method) {
            return findAnnotatedMethod(annotatedType, (Method) element);
        }
        if (element instanceof Constructor) {
            return findAnnotatedConstructor(annotatedType, (Constructor<?>) element);
        }
        if (element instanceof Parameter) {
            return findAnnotatedParameter(annotatedType, (Parameter) element);
        }
        return null;
    }

    public static Type baseTypeOf(AnnotatedType<?> annotatedType, Field field) {
        AnnotatedField<?> annotatedField = findAnnotatedField(annotatedType, field);
        return annotatedField != null ? annotatedField.getBaseType() : field.getGenericType();
    }

    public static Type baseTypeOf(AnnotatedType<?> annotatedType, Method method) {
        AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, method);
        return annotatedMethod != null ? annotatedMethod.getBaseType() : method.getGenericReturnType();
    }

    public static Type baseTypeOf(AnnotatedType<?> annotatedType, Parameter parameter) {
        AnnotatedParameter<?> annotatedParameter = findAnnotatedParameter(annotatedType, parameter);
        return annotatedParameter != null ? annotatedParameter.getBaseType() : parameter.getParameterizedType();
    }

    public static Set<Type> typeClosureOf(AnnotatedType<?> annotatedType, Field field) {
        AnnotatedField<?> annotatedField = findAnnotatedField(annotatedType, field);
        if (annotatedField != null && annotatedField.getTypeClosure() != null && !annotatedField.getTypeClosure().isEmpty()) {
            return new LinkedHashSet<>(annotatedField.getTypeClosure());
        }
        return TypeClosureHelper.extractTypesFromType(field.getGenericType());
    }

    public static Set<Type> typeClosureOf(AnnotatedType<?> annotatedType, Method method) {
        AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, method);
        if (annotatedMethod != null && annotatedMethod.getTypeClosure() != null && !annotatedMethod.getTypeClosure().isEmpty()) {
            return new LinkedHashSet<>(annotatedMethod.getTypeClosure());
        }
        return TypeClosureHelper.extractTypesFromType(method.getGenericReturnType());
    }

    public static AnnotatedField<?> findAnnotatedField(AnnotatedType<?> annotatedType, Field field) {
        if (annotatedType == null || field == null) {
            return null;
        }
        for (AnnotatedField<?> annotatedField : annotatedType.getFields()) {
            if (matchesMember(annotatedField.getJavaMember(), field)) {
                return annotatedField;
            }
        }
        return null;
    }

    public static AnnotatedMethod<?> findAnnotatedMethod(AnnotatedType<?> annotatedType, Method method) {
        if (annotatedType == null || method == null) {
            return null;
        }
        for (AnnotatedMethod<?> annotatedMethod : annotatedType.getMethods()) {
            if (matchesMember(annotatedMethod.getJavaMember(), method)) {
                return annotatedMethod;
            }
        }
        return null;
    }

    public static AnnotatedConstructor<?> findAnnotatedConstructor(AnnotatedType<?> annotatedType, Constructor<?> constructor) {
        if (annotatedType == null || constructor == null) {
            return null;
        }
        for (AnnotatedConstructor<?> annotatedConstructor : annotatedType.getConstructors()) {
            if (matchesMember(annotatedConstructor.getJavaMember(), constructor)) {
                return annotatedConstructor;
            }
        }
        return null;
    }

    public static AnnotatedParameter<?> findAnnotatedParameter(AnnotatedType<?> annotatedType, Parameter parameter) {
        if (annotatedType == null || parameter == null) {
            return null;
        }
        Executable declaringExecutable = parameter.getDeclaringExecutable();
        int position = parameterPosition(parameter);
        if (position < 0) {
            return null;
        }

        if (declaringExecutable instanceof Method) {
            AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, (Method) declaringExecutable);
            return parameterAt(annotatedMethod, position);
        }
        if (declaringExecutable instanceof Constructor<?>) {
            AnnotatedConstructor<?> annotatedConstructor =
                    findAnnotatedConstructor(annotatedType, (Constructor<?>) declaringExecutable);
            return parameterAt(annotatedConstructor, position);
        }
        return null;
    }

    private static AnnotatedParameter<?> parameterAt(AnnotatedCallable<?> callable, int position) {
        if (callable == null) {
            return null;
        }
        for (AnnotatedParameter<?> parameter : callable.getParameters()) {
            if (parameter.getPosition() == position) {
                return parameter;
            }
        }
        return null;
    }

    private static int parameterPosition(Parameter parameter) {
        Parameter[] parameters = parameter.getDeclaringExecutable().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].equals(parameter)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesMember(Member lhs, Member rhs) {
        return lhs != null && lhs.equals(rhs);
    }

    private static boolean isMemberElement(AnnotatedElement element) {
        return element instanceof Field
                || element instanceof Method
                || element instanceof Constructor
                || element instanceof Parameter;
    }
}
