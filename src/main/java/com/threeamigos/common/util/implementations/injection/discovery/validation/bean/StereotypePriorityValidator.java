package com.threeamigos.common.util.implementations.injection.discovery.validation.bean;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import jakarta.enterprise.inject.spi.DefinitionException;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getNamedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getTargetAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNamedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasQualifierAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasStereotypeAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasTargetAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasTypedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.*;

/**
 * Validates stereotype-related declarations and collects stereotype priorities.
 */
public final class StereotypePriorityValidator {

    private final Predicate<Class<? extends Annotation>> scopeAnnotationTypePredicate;

    public StereotypePriorityValidator(Predicate<Class<? extends Annotation>> scopeAnnotationTypePredicate) {
        this.scopeAnnotationTypePredicate = scopeAnnotationTypePredicate;
    }

    @SuppressWarnings("unchecked")
    public void validateStereotypeScopeDeclaration(Class<?> clazz) {
        if (!clazz.isAnnotation()) {
            return;
        }

        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) clazz;
        if (!hasStereotypeAnnotation(annotationType)) {
            return;
        }

        Set<Class<? extends Annotation>> declaredScopes = new LinkedHashSet<>();
        collectStereotypeScopes(annotationType, declaredScopes, new HashSet<>());

        if (declaredScopes.size() > 1) {
            String scopeNames = declaredScopes.stream()
                    .map(scope -> "@" + scope.getSimpleName())
                    .collect(Collectors.joining(", "));
            throw new DefinitionException(annotationType.getName() +
                    ": stereotype declares multiple scopes: " + scopeNames);
        }
    }

    @SuppressWarnings("unchecked")
    public void validateStereotypeNamedDeclaration(Class<?> clazz) {
        if (!clazz.isAnnotation()) {
            return;
        }

        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) clazz;
        if (!hasStereotypeAnnotation(annotationType)) {
            return;
        }

        validateStereotypeNamedDeclaration(annotationType, new HashSet<>());
    }

    public void validateStereotypePriorityDeclaration(Class<?> clazz,
                                                      Integer explicitPriority,
                                                      Annotation[] effectiveAnnotations) {
        if (explicitPriority != null) {
            // Explicit bean @Priority always wins over stereotype priorities.
            return;
        }

        Set<Integer> stereotypePriorities = collectStereotypePriorityValues(effectiveAnnotations);
        if (stereotypePriorities.size() > 1) {
            String priorityValues = stereotypePriorities.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            throw new DefinitionException(clazz.getName() +
                    ": stereotypes declare different @Priority values (" +
                    priorityValues +
                    "). Bean must explicitly declare @Priority.");
        }
    }

    /**
     * Collects all transitive stereotype priorities declared on the effective annotations of a bean class.
     */
    public Set<Integer> collectStereotypePriorityValues(Annotation[] effectiveAnnotations) {
        Set<Integer> priorities = new LinkedHashSet<>();
        Set<Class<? extends Annotation>> visited = new HashSet<>();
        if (effectiveAnnotations == null) {
            return priorities;
        }

        for (Annotation annotation : effectiveAnnotations) {
            if (annotation == null) {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasStereotypeAnnotation(annotationType)) {
                collectStereotypePriorityValues(annotationType, priorities, visited);
            }
        }

        return priorities;
    }

    /**
     * CDI 4.1 §2.8.1.6:
     * Stereotypes declared @Target(TYPE) may not be applied to stereotypes that can target
     * METHOD and/or FIELD.
     */
    @SuppressWarnings("unchecked")
    public void validateStereotypeTargetCompatibility(Class<?> clazz) {
        if (!clazz.isAnnotation()) {
            return;
        }

        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) clazz;
        if (!hasStereotypeAnnotation(annotationType)) {
            return;
        }

        Set<ElementType> declaredTargets = declaredTargetElements(annotationType);
        boolean canTargetMethodOrField = declaredTargets.contains(ElementType.METHOD) ||
                declaredTargets.contains(ElementType.FIELD);
        if (!canTargetMethodOrField) {
            return;
        }

        Set<String> invalidStereotypes = new LinkedHashSet<>();
        collectTypeOnlyStereotypes(annotationType, invalidStereotypes, new HashSet<>());
        if (!invalidStereotypes.isEmpty()) {
            throw new DefinitionException(annotationType.getName() +
                    ": declares stereotype(s) " + String.join(", ", invalidStereotypes) +
                    " with @Target(TYPE), which is not allowed for stereotypes targeting METHOD/FIELD.");
        }
    }

    @SuppressWarnings("unchecked")
    public void validateStereotypeNonPortableDeclarations(Class<?> clazz) {
        if (!clazz.isAnnotation()) {
            return;
        }

        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) clazz;
        if (!hasStereotypeAnnotation(annotationType)) {
            return;
        }

        if (hasTypedAnnotation(annotationType)) {
            throw new NonPortableBehaviourException(annotationType.getName() +
                    ": stereotype is annotated with @Typed");
        }

        List<String> illegalQualifiers = new ArrayList<>();
        for (Annotation meta : annotationType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasQualifierAnnotation(metaType) && !hasNamedAnnotation(metaType)) {
                illegalQualifiers.add("@" + metaType.getSimpleName());
            }
        }

        if (!illegalQualifiers.isEmpty()) {
            throw new NonPortableBehaviourException(annotationType.getName() +
                    ": stereotype declares qualifier(s) other than @Named: " +
                    String.join(", ", illegalQualifiers));
        }
    }

    private void validateStereotypeNamedDeclaration(Class<? extends Annotation> stereotypeType,
                                                    Set<Class<? extends Annotation>> visited) {
        if (!visited.add(stereotypeType)) {
            return;
        }

        Annotation named = getNamedAnnotation(stereotypeType);
        if (named != null) {
            String value = getNamedValue(named);
            if (value != null && !value.trim().isEmpty()) {
                throw new DefinitionException(stereotypeType.getName() +
                        ": stereotype declares non-empty @Named(\"" + value + "\")");
            }
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasStereotypeAnnotation(metaType)) {
                validateStereotypeNamedDeclaration(metaType, visited);
            }
        }
    }

    private void collectStereotypeScopes(Class<? extends Annotation> stereotypeType,
                                         Set<Class<? extends Annotation>> scopes,
                                         Set<Class<? extends Annotation>> visited) {
        if (!visited.add(stereotypeType)) {
            return;
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (scopeAnnotationTypePredicate.test(metaType)) {
                scopes.add(metaType);
            } else if (hasStereotypeAnnotation(metaType)) {
                collectStereotypeScopes(metaType, scopes, visited);
            }
        }
    }

    private void collectTypeOnlyStereotypes(Class<? extends Annotation> stereotypeType,
                                            Set<String> invalidStereotypes,
                                            Set<Class<? extends Annotation>> visited) {
        if (!visited.add(stereotypeType)) {
            return;
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (!hasStereotypeAnnotation(metaType)) {
                continue;
            }

            if (isTypeOnlyTarget(metaType)) {
                invalidStereotypes.add("@" + metaType.getSimpleName());
            }

            collectTypeOnlyStereotypes(metaType, invalidStereotypes, visited);
        }
    }

    private Set<ElementType> declaredTargetElements(Class<? extends Annotation> annotationType) {
        Target target = hasTargetAnnotation(annotationType) ? getTargetAnnotation(annotationType) : null;
        if (target == null || target.value() == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(Arrays.asList(target.value()));
    }

    private boolean isTypeOnlyTarget(Class<? extends Annotation> annotationType) {
        Set<ElementType> targetElements = declaredTargetElements(annotationType);
        return targetElements.size() == 1 && targetElements.contains(ElementType.TYPE);
    }

    private void collectStereotypePriorityValues(Class<? extends Annotation> stereotypeType,
                                                 Set<Integer> priorities,
                                                 Set<Class<? extends Annotation>> visited) {
        if (!visited.add(stereotypeType)) {
            return;
        }

        Integer declaredPriority = getPriorityValue(stereotypeType.getAnnotations());
        if (declaredPriority != null) {
            priorities.add(declaredPriority);
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasStereotypeAnnotation(metaType)) {
                collectStereotypePriorityValues(metaType, priorities, visited);
            }
        }
    }

}
