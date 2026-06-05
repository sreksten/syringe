package com.threeamigos.common.util.implementations.injection.annotations;

import com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper;
import jakarta.enterprise.inject.spi.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasAnyAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.PRIORITY;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.WITH_ANNOTATIONS;

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

    public static AnnotatedParameter<?> findAnnotatedParameter(AnnotatedMethod<?> annotatedMethod, int position) {
        if (annotatedMethod == null) {
            return null;
        }
        for (AnnotatedParameter<?> parameter : annotatedMethod.getParameters()) {
            if (parameter.getPosition() == position) {
                return parameter;
            }
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

    public static jakarta.enterprise.event.Observes getObservesAnnotationFrom(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.enterprise.event.Observes) {
                return (jakarta.enterprise.event.Observes) annotation;
            }
        }
        return null;
    }

    public static boolean hasObservesAsyncAnnotationIn(Annotation[] annotations) {
        return getObservesAsyncAnnotationFrom(annotations) != null;
    }

    public static jakarta.enterprise.event.ObservesAsync getObservesAsyncAnnotationFrom(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.enterprise.event.ObservesAsync) {
                return (jakarta.enterprise.event.ObservesAsync) annotation;
            }
        }
        return null;
    }

    public static Set<Annotation> extractObserverQualifiers(Annotation[] observedParameterAnnotations) {
        if (observedParameterAnnotations == null) {
            return new HashSet<>();
        }
        return new HashSet<>(
                QualifiersHelper
                        .extractQualifierAnnotations(observedParameterAnnotations));
    }

    public static boolean hasObservesAnnotationIn(Annotation[] annotations) {
        return getObservesAnnotationFrom(annotations) != null;
    }

    public static Integer getPriorityValueFromAnnotations(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (PRIORITY.matches(annotation.annotationType())) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean hasDisposesAnnotationInAnnotatedParameter(AnnotatedParameter<?> parameter) {
        if (parameter == null || parameter.getAnnotations() == null) {
            return false;
        }
        for (Annotation annotation : parameter.getAnnotations()) {
            if (annotation != null && hasDisposesAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Set<Class<? extends Annotation>> resolveWithAnnotationsFilter(Parameter parameter) {
        if (!hasWithAnnotationsAnnotation(parameter)) {
            return null;
        }
        Annotation[] annotations = parameter.getAnnotations();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!WITH_ANNOTATIONS.matches(annotationType)) {
                continue;
            }
            try {
                Method valueMethod = annotationType.getMethod("value");
                Object value = valueMethod.invoke(annotation);
                if (!(value instanceof Class[])) {
                    return Collections.emptySet();
                }
                Class<?>[] rawValues = (Class<?>[]) value;
                Set<Class<? extends Annotation>> filter = new LinkedHashSet<>();
                for (Class<?> rawValue : rawValues) {
                    if (rawValue != null && Annotation.class.isAssignableFrom(rawValue)) {
                        filter.add((Class<? extends Annotation>) rawValue);
                    }
                }
                return filter;
            } catch (Exception e) {
                throw new DefinitionException("Unable to read @WithAnnotations value on parameter " + parameter, e);
            }
        }
        return null;
    }

    public static boolean hasNoQualifierOrOnlyAnyQualifier(Parameter observedParameter) {
        List<Annotation> qualifierAnnotations = new ArrayList<>();
        for (Annotation annotation : observedParameter.getAnnotations()) {
            if (hasQualifierAnnotation(annotation.annotationType())) {
                qualifierAnnotations.add(annotation);
            }
        }

        if (qualifierAnnotations.isEmpty()) {
            return true;
        }

        return qualifierAnnotations.size() == 1 &&
                hasAnyAnnotation(qualifierAnnotations.get(0).annotationType());
    }

}
