package com.threeamigos.common.util.implementations.injection.annotations;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNamedAnnotation;

public class AnnotationsHelper {

    private AnnotationsHelper() {
    }

    @Nonnull
    public static String toList(Collection<Annotation> annotationDef) {
        String metaAnnotationList;
        if (annotationDef != null && !annotationDef.isEmpty()) {
            metaAnnotationList = toList(annotationDef.stream());
        } else {
            metaAnnotationList = "[]";
        }
        return metaAnnotationList;
    }

    @Nonnull
    public static String toList(Annotation[] annotationDef) {
        String metaAnnotationList;
        if (annotationDef != null && annotationDef.length > 0) {
            metaAnnotationList = toList(Stream.of(annotationDef));
        } else {
            metaAnnotationList = "[]";
        }
        return metaAnnotationList;
    }

    private static String toList(Stream<Annotation> annotationDef) {
        return "[" +
                annotationDef
                        .map(def -> "@" + def.annotationType().getSimpleName())
                        .collect(Collectors.joining(", "))
                 + "]";
    }

    public static boolean hasAnnotation(AnnotatedElement element, AnnotationsEnum annotation) {
        if (element == null || annotation == null) {
            return false;
        }
        return hasAnnotation(element.getAnnotations(), annotation);
    }

    public static boolean hasAnnotation(Annotation[] annotations, AnnotationsEnum annotation) {
        if (annotations == null || annotation == null) {
            return false;
        }
        for (Annotation candidate : annotations) {
            if (candidate != null && annotation.matches(candidate.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnnotation(Iterable<? extends Annotation> annotations, AnnotationsEnum annotation) {
        if (annotations == null || annotation == null) {
            return false;
        }
        for (Annotation candidate : annotations) {
            if (candidate != null && annotation.matches(candidate.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAroundInvokeAnnotation(Iterable<? extends Annotation> annotations) {
        return hasAnnotation(annotations, AnnotationsEnum.AROUND_INVOKE);
    }

    public static boolean hasAroundInvokeAnnotation(Method method, AnnotatedType<?> override) {
        if (method == null) {
            return false;
        }
        Annotation[] annotations = override != null
                ? AnnotatedMetadataHelper.annotationsOf(override, method)
                : method.getAnnotations();
        return hasAnnotation(annotations, AnnotationsEnum.AROUND_INVOKE);
    }

    public static boolean hasExcludeClassInterceptorsAnnotation(Annotation[] annotations) {
        return hasAnnotation(annotations, AnnotationsEnum.EXCLUDE_CLASS_INTERCEPTORS);
    }

    public static boolean isInterceptorBindingMetaAnnotation(Class<? extends Annotation> annotationType) {
        return annotationType != null && AnnotationsEnum.INTERCEPTOR_BINDING.matches(annotationType);
    }

    public static boolean hasAnyRequiredAnnotation(AnnotatedElement element,
                                                   Class<? extends Annotation>[] requiredAnnotations) {
        if (element == null || requiredAnnotations == null) {
            return false;
        }
        for (Class<? extends Annotation> annotation : requiredAnnotations) {
            if (annotation != null && element.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRequiredEnhancementAnnotation(AnnotatedElement element,
                                                           Class<? extends Annotation>[] requiredAnnotations) {
        if (element == null) {
            return false;
        }

        if (hasAnyRequiredAnnotation(element, requiredAnnotations)) {
            return true;
        }

        if (!(element instanceof Class<?>)) {
            return false;
        }

        Class<?> clazz = (Class<?>) element;
        for (Field field : clazz.getDeclaredFields()) {
            if (hasAnyRequiredAnnotation(field, requiredAnnotations)) {
                return true;
            }
        }

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (hasAnyRequiredAnnotation(constructor, requiredAnnotations) ||
                    parametersHaveAnyRequiredAnnotation(constructor.getParameters(), requiredAnnotations)) {
                return true;
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (hasAnyRequiredAnnotation(method, requiredAnnotations) ||
                    parametersHaveAnyRequiredAnnotation(method.getParameters(), requiredAnnotations)) {
                return true;
            }
        }

        return false;
    }

    public static boolean parametersHaveAnyRequiredAnnotation(Parameter[] parameters,
                                                              Class<? extends Annotation>[] requiredAnnotations) {
        if (parameters == null || requiredAnnotations == null) {
            return false;
        }
        for (Parameter parameter : parameters) {
            if (hasAnyRequiredAnnotation(parameter, requiredAnnotations)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasInjectAnnotation(Collection<? extends Annotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && AnnotationPredicates.hasInjectAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isScopeOrNormalScopeAnnotation(Class<? extends Annotation> annotationType) {
        return AnnotationPredicates.hasScopeAnnotation(annotationType)
                || AnnotationPredicates.hasNormalScopeAnnotation(annotationType);
    }

    public static boolean isCdiInheritableTypeAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        if (isScopeOrNormalScopeAnnotation(annotationType)) {
            return true;
        }
        return AnnotationPredicates.hasQualifierAnnotation(annotationType)
                || AnnotationPredicates.hasStereotypeAnnotation(annotationType)
                || AnnotationPredicates.hasInterceptorBindingAnnotation(annotationType);
    }

    public static String readNamedValue(Annotation namedAnnotation) {
        try {
            Method value = namedAnnotation.annotationType().getMethod("value");
            Object raw = value.invoke(namedAnnotation);
            return raw == null ? "" : raw.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static Annotation findNamedQualifier(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (hasNamedAnnotation(annotation.annotationType())) {
                return annotation;
            }
        }
        return null;
    }

}
