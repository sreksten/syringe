package com.threeamigos.common.util.implementations.injection.annotations.legacy;

import com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor;
import jakarta.enterprise.inject.spi.DefinitionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasAnyAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNewAnnotation;

/**
 * Helper utilities for legacy CDI 1.0 {@code @javax.enterprise.inject.New} support.
 */
public final class LegacyNewQualifierHelper {

    private LegacyNewQualifierHelper() {
    }

    /**
     * Returns selection metadata for {@code @New} usage, or {@code null} when no @New qualifier is present.
     */
    public static LegacyNewSelection extractSelection(Type requiredType, Annotation[] qualifiers) {
        Annotation newQualifier = null;
        List<String> invalidExtraQualifiers = new ArrayList<>();
        if (qualifiers != null) {
            for (Annotation qualifier : qualifiers) {
                if (qualifier == null) {
                    continue;
                }
                if (isLegacyNewQualifier(qualifier)) {
                    newQualifier = qualifier;
                    continue;
                }
                if (!isAnyQualifier(qualifier)) {
                    invalidExtraQualifiers.add(qualifier.annotationType().getName());
                }
            }
        }

        if (newQualifier == null) {
            return null;
        }

        if (!invalidExtraQualifiers.isEmpty()) {
            throw new DefinitionException("@New injection points may not declare additional qualifiers: "
                    + String.join(", ", invalidExtraQualifiers));
        }

        Class<?> requiredRawType = RawTypeExtractor.getRawType(requiredType);
        if (requiredRawType == null) {
            throw new DefinitionException("@New cannot be used with unresolved required type: " + requiredType);
        }

        Class<?> targetClass = resolveTargetClass(newQualifier, requiredRawType);
        if (!requiredRawType.isAssignableFrom(targetClass)) {
            throw new DefinitionException("@New target " + targetClass.getName()
                    + " is not assignable to required type " + requiredRawType.getName());
        }

        validateTargetClass(targetClass);
        return new LegacyNewSelection(targetClass, newQualifier);
    }

    public static boolean isLegacyNewQualifier(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        Class<? extends Annotation> annotationType = annotation.annotationType();
        return hasNewAnnotation(annotationType);
    }

    private static boolean isAnyQualifier(Annotation annotation) {
        return annotation != null && hasAnyAnnotation(annotation.annotationType());
    }

    private static Class<?> resolveTargetClass(Annotation newQualifier, Class<?> requiredRawType) {
        try {
            Method valueMethod = newQualifier.annotationType().getMethod("value");
            Object value = valueMethod.invoke(newQualifier);
            if (!(value instanceof Class<?>)) {
                throw new DefinitionException("@New qualifier value is not a class: " + value);
            }

            Class<?> configured = (Class<?>) value;
            if (configured.equals(newQualifier.annotationType())) {
                return requiredRawType;
            }
            if (Annotation.class.isAssignableFrom(configured)) {
                @SuppressWarnings("unchecked")
                Class<? extends Annotation> configuredAnnotation = (Class<? extends Annotation>) configured;
                if (hasNewAnnotation(configuredAnnotation)) {
                    return requiredRawType;
                }
            }
            return configured;
        } catch (NoSuchMethodException e) {
            throw new DefinitionException("@New qualifier does not declare a value() member", e);
        } catch (IllegalAccessException e) {
            throw new DefinitionException("Unable to access @New qualifier value()", e);
        } catch (InvocationTargetException e) {
            throw new DefinitionException("Unable to read @New qualifier value()", e.getTargetException());
        }
    }

    private static void validateTargetClass(Class<?> targetClass) {
        if (targetClass.isInterface()) {
            throw new DefinitionException("@New target must be a concrete class, found interface "
                    + targetClass.getName());
        }
        if (targetClass.isAnnotation()) {
            throw new DefinitionException("@New target must be a concrete class, found annotation "
                    + targetClass.getName());
        }
        if (targetClass.isPrimitive()) {
            throw new DefinitionException("@New target must be a concrete class, found primitive "
                    + targetClass.getName());
        }
        if (targetClass.isArray()) {
            throw new DefinitionException("@New target must be a concrete class, found array "
                    + targetClass.getName());
        }
    }

    public static final class LegacyNewSelection {
        private final Class<?> targetClass;
        private final Annotation newQualifier;

        private LegacyNewSelection(Class<?> targetClass, Annotation newQualifier) {
            this.targetClass = targetClass;
            this.newQualifier = newQualifier;
        }

        public Class<?> getTargetClass() {
            return targetClass;
        }

        public Annotation getNewQualifier() {
            return newQualifier;
        }

        public List<Annotation> asQualifierView() {
            return Collections.singletonList(newQualifier);
        }
    }
}
