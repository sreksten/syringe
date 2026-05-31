package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.PRIORITY;

/**
 * Shared qualifier utilities used across resolution components.
 */
public final class QualifiersHelper {

    private QualifiersHelper() {}

    /**
     * Extracts qualifiers from an annotation array, defaulting to @Default when empty.
     * This is intended for required-qualifier extraction during resolution.
     */
    public static Set<Annotation> extractQualifiers(Annotation[] annotations) {
        Set<Annotation> qualifiers = extractQualifierAnnotations(annotations);
        if (qualifiers.isEmpty()) {
            qualifiers.add(new DefaultLiteral());
        }
        return qualifiers;
    }

    /**
     * Normalizes a collection of annotations to a qualifier set (adds @Default if none).
     * This is intended for required/available qualifier matching utilities.
     */
    public static Set<Annotation> normalizeQualifiers(Collection<Annotation> annotations) {
        Set<Annotation> qualifiers = annotations == null ? new HashSet<>() :
                extractQualifierAnnotations(annotations.toArray(new Annotation[0]));
        if (qualifiers.isEmpty()) {
            qualifiers.add(new DefaultLiteral());
        }
        return qualifiers;
    }

    /**
     * Extracts qualifier annotations from an annotation array without adding implicit qualifiers.
     */
    public static Set<Annotation> extractQualifierAnnotations(Annotation[] annotations) {
        Set<Annotation> qualifiers = new HashSet<>();
        if (annotations != null) {
            for (Annotation ann : annotations) {
                collectQualifierAnnotation(ann, qualifiers);
            }
        }
        return qualifiers;
    }

    /**
     * Extracts bean qualifiers from an annotation array and applies CDI built-ins:
     * adds {@code @Default} when no qualifier other than {@code @Named}/{@code @Any} exists,
     * and always adds {@code @Any}.
     */
    public static Set<Annotation> extractBeanQualifiers(Annotation[] annotations) {
        return normalizeBeanQualifiers(Arrays.asList(annotations == null ? new Annotation[0] : annotations));
    }

    /**
     * Normalizes bean qualifiers according to CDI bean qualifier rules:
     * adds {@code @Default} when no qualifier other than {@code @Named}/{@code @Any} exists,
     * and always adds {@code @Any}.
     */
    public static Set<Annotation> normalizeBeanQualifiers(Collection<Annotation> annotations) {
        Set<Annotation> qualifiers = annotations == null ? new HashSet<>() :
                extractQualifierAnnotations(annotations.toArray(new Annotation[0]));

        boolean hasNonNamedNonAnyQualifier = qualifiers.stream()
                .map(Annotation::annotationType)
                .anyMatch(type -> !hasNamedAnnotation(type) && !hasAnyAnnotation(type) && !hasDefaultAnnotation(type));

        if (hasNonNamedNonAnyQualifier) {
            qualifiers.removeIf(q -> hasDefaultAnnotation(q.annotationType()));
        } else {
            qualifiers.add(new DefaultLiteral());
        }
        qualifiers.add(new AnyLiteral());
        return qualifiers;
    }

    /**
     * Collects qualifier annotations, expanding repeatable qualifier container annotations.
     */
    private static void collectQualifierAnnotation(Annotation annotation, Set<Annotation> sink) {
        if (annotation == null) {
            return;
        }

        if (hasQualifierAnnotation(annotation.annotationType())) {
            sink.add(annotation);
            return;
        }

        Collections.addAll(sink, extractQualifierAnnotationsFromContainer(annotation));
    }

    /**
     * Extracts nested qualifier annotations from a repeatable container annotation.
     *
     * <p>For example, for {@code @Locations({@Location("north"), @Location("south")})}
     * this returns the nested {@code @Location} annotations.
     */
    private static Annotation[] extractQualifierAnnotationsFromContainer(Annotation containerAnnotation) {
        try {
            Method valueMethod = containerAnnotation.annotationType().getMethod("value");
            Class<?> returnType = valueMethod.getReturnType();
            if (!returnType.isArray()) {
                return new Annotation[0];
            }

            Class<?> componentType = returnType.getComponentType();
            if (componentType == null || !Annotation.class.isAssignableFrom(componentType)) {
                return new Annotation[0];
            }

            @SuppressWarnings("unchecked")
            Class<? extends Annotation> nestedAnnotationType = (Class<? extends Annotation>) componentType;
            if (!hasQualifierAnnotation(nestedAnnotationType)) {
                return new Annotation[0];
            }

            Object value = valueMethod.invoke(containerAnnotation);
            if (value instanceof Annotation[]) {
                return (Annotation[]) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Not a repeatable qualifier container annotation; ignore.
        }
        return new Annotation[0];
    }

    public static boolean isQualifierAnnotation(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        Class<? extends Annotation> annotationType = annotation.annotationType();
        return hasQualifierAnnotation(annotationType) || hasNamedAnnotation(annotationType);
    }

    public static boolean isAnyQualifier(Annotation annotation) {
        return annotation != null && hasAnyAnnotation(annotation.annotationType());
    }

    /**
     * Returns true if the available set contains all required qualifiers, respecting @Named values
     * and @Nonbinding semantics.
     */
    public static boolean qualifiersMatch(Set<Annotation> requiredQualifiers, Set<Annotation> availableQualifiers) {
        // Special case: @Named requires an exact match
        Annotation requiredNamed = findNamedAnnotation(requiredQualifiers);
        Annotation availableNamed = findNamedAnnotation(availableQualifiers);

        if (requiredNamed != null) {
            if (availableNamed == null) {
                return false;
            }
            if (!getNamedValue(requiredNamed).equals(getNamedValue(availableNamed))) {
                return false;
            }
        }

        for (Annotation required : requiredQualifiers) {
            if (hasAnyAnnotation(required.annotationType())) {
                continue;
            }
            if (hasNamedAnnotation(required.annotationType())) {
                continue;
            }
            boolean found = false;
            for (Annotation avail : availableQualifiers) {
                if (qualifiersEqual(required, avail)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Event-observer qualifier matching where @Default observer methods only match
     * events with no explicit non-default qualifiers.
     */
    public static boolean notEventQualifiersMatch(Set<Annotation> observedQualifiers, Set<Annotation> eventQualifiers) {
        Annotation observedNamed = findNamedAnnotation(observedQualifiers);
        Annotation eventNamed = findNamedAnnotation(eventQualifiers);

        if (observedNamed != null) {
            if (eventNamed == null) {
                return true;
            }
            if (!getNamedValue(observedNamed).equals(getNamedValue(eventNamed))) {
                return true;
            }
        }

        for (Annotation required : observedQualifiers) {
            if (hasAnyAnnotation(required.annotationType())) {
                continue;
            }
            if (isDefaultQualifier(required)) {
                if (hasExplicitNonDefaultQualifier(eventQualifiers)) {
                    return true;
                }
                continue;
            }
            if (hasNamedAnnotation(required.annotationType())) {
                continue;
            }
            boolean found = false;
            for (Annotation avail : eventQualifiers) {
                if (qualifiersEqual(required, avail)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitNonDefaultQualifier(Set<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return false;
        }
        for (Annotation qualifier : qualifiers) {
            Class<? extends Annotation> type = qualifier.annotationType();
            if (hasAnyAnnotation(type)) {
                continue;
            }
            if (hasNamedAnnotation(type)) {
                continue;
            }
            if (isDefaultQualifier(qualifier)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static boolean isDefaultQualifier(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        return hasDefaultAnnotation(annotation.annotationType());
    }

    private static Annotation findNamedAnnotation(Set<Annotation> annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation ann : annotations) {
            if (hasNamedAnnotation(ann.annotationType())) {
                return ann;
            }
        }
        return null;
    }

    public static boolean qualifiersEqual(Annotation q1, Annotation q2) {
        return AnnotationComparator.equals(q1, q2);
    }

    public static Annotation findAnnotation(Set<Annotation> annotations, Class<? extends Annotation> type) {
        if (annotations == null) {
            return null;
        }
        for (Annotation ann : annotations) {
            if (ann.annotationType().equals(type)) {
                return ann;
            }
        }
        return null;
    }

    public static String getNamedValue(Annotation namedAnnotation) {
        try {
            Method value = namedAnnotation.annotationType().getMethod("value");
            Object raw = value.invoke(namedAnnotation);
            return raw == null ? "" : raw.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static Integer getPriorityValue(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && PRIORITY.matches(annotation.annotationType())) {
                return getPriorityValue(annotation);
            }
        }
        return null;
    }

    public static Integer getPriorityValue(Annotation priorityAnnotation) {
        try {
            Method valueMethod = priorityAnnotation.annotationType().getMethod("value");
            Object value = valueMethod.invoke(priorityAnnotation);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore malformed annotation implementations and treat as absent.
        }
        return null;
    }
}
