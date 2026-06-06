package com.threeamigos.common.util.implementations.injection.discovery.validation.bean;

import com.threeamigos.common.util.implementations.injection.discovery.validation.CDI41BeanValidator;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import jakarta.enterprise.context.Dependent;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getNamedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.readNamedValue;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasInheritedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNamedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.normalizeSingletonToApplicationScoped;
import static com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.extractQualifierAnnotations;
import static com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.normalizeBeanQualifiers;
import static com.threeamigos.common.util.implementations.injection.types.ClassHelper.defaultedBeanName;

/**
 * Extracted bean-attributes extraction rules for CDI41BeanValidator.
 */
public class BeanAttributesExtractor {

    private final KnowledgeBase knowledgeBase;
    private final CDI41BeanValidator validator;

    public BeanAttributesExtractor(KnowledgeBase knowledgeBase, CDI41BeanValidator validator) {
        this.knowledgeBase = knowledgeBase;
        this.validator = validator;
    }

    public String extractBeanName(Class<?> clazz) {
        for (Annotation annotation : validator.annotationsOf(clazz)) {
            if (hasNamedAnnotation(annotation.annotationType())) {
                return defaultedBeanName(readNamedValue(annotation), clazz);
            }
        }

        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Annotation annotation : validator.annotationsOf(clazz)) {
            Class<? extends Annotation> at = annotation.annotationType();
            if (validator.isStereotypeAnnotationType(at)) {
                String stereotypeName = extractNameFromStereotype(at, clazz, visited);
                if (stereotypeName != null) {
                    return stereotypeName;
                }
            }
        }

        return "";
    }

    public Set<Annotation> extractBeanQualifiers(Class<?> clazz) {
        Set<Annotation> result = extractQualifierAnnotations(validator.annotationsOf(clazz));
        for (Annotation annotation : validator.annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isStereotypeAnnotationType(annotationType)) {
                result.addAll(extractQualifiersFromStereotype(annotationType));
            }
            if (validator.isQualifierAnnotationType(annotationType)) {
                result.add(annotation);
            }
        }
        return normalizeBeanQualifiers(result);
    }

    public Class<? extends Annotation> extractBeanScope(Class<?> clazz) {
        for (Annotation annotation : validator.declaredAnnotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isScopeAnnotationType(annotationType)) {
                return normalizeSingletonToApplicationScoped(annotationType);
            }
        }

        Class<? extends Annotation> inheritedClassScope = resolveInheritedScopeByCdiRules(clazz);
        if (inheritedClassScope != null) {
            return inheritedClassScope;
        }

        Class<? extends Annotation> inheritedScope = null;
        for (Annotation annotation : validator.annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!validator.isStereotypeAnnotationType(annotationType)) {
                continue;
            }
            Class<? extends Annotation> stereotypeScope = extractScopeFromStereotype(annotationType);
            if (stereotypeScope == null) {
                continue;
            }
            if (inheritedScope == null) {
                inheritedScope = stereotypeScope;
                continue;
            }
            if (!inheritedScope.equals(stereotypeScope)) {
                knowledgeBase.addDefinitionError(clazz.getName() +
                        ": conflicting scopes inherited from stereotypes (" +
                        inheritedScope.getName() + " vs " + stereotypeScope.getName() +
                        "). Declare an explicit scope on the bean to resolve.");
            }
        }

        if (inheritedScope != null) {
            return inheritedScope;
        }

        return Dependent.class;
    }

    public Set<Class<? extends Annotation>> extractBeanStereotypes(Class<?> clazz) {
        Set<Class<? extends Annotation>> stereotypes = new HashSet<>();
        for (Annotation annotation : validator.annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isStereotypeAnnotationType(annotationType)) {
                stereotypes.add(annotationType);
            }
        }
        return stereotypes;
    }

    private String extractNameFromStereotype(Class<? extends Annotation> stereotypeAnnotation,
                                             Class<?> beanClass,
                                             Set<Class<? extends Annotation>> visited) {
        if (!visited.add(stereotypeAnnotation)) {
            return null;
        }

        if (knowledgeBase.isRegisteredStereotype(stereotypeAnnotation)) {
            Set<Annotation> definition = knowledgeBase.getStereotypeDefinition(stereotypeAnnotation);
            if (definition != null) {
                for (Annotation meta : definition) {
                    if (meta == null) {
                        continue;
                    }
                    if (hasNamedAnnotation(meta.annotationType())) {
                        return defaultedBeanName(readNamedValue(meta), beanClass);
                    }
                }
            }
        }

        Annotation named = getNamedAnnotation(stereotypeAnnotation);
        if (named != null) {
            return defaultedBeanName(readNamedValue(named), beanClass);
        }

        for (Annotation meta : stereotypeAnnotation.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (validator.isStereotypeAnnotationType(metaType)) {
                String nested = extractNameFromStereotype(metaType, beanClass, visited);
                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private Class<? extends Annotation> resolveInheritedScopeByCdiRules(Class<?> clazz) {
        Class<?> current = clazz.getSuperclass();
        while (current != null && current != Object.class) {
            Class<? extends Annotation> declaredScope = firstDeclaredScope(current);
            if (declaredScope != null) {
                return hasInheritedAnnotation(declaredScope) ? declaredScope : null;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Class<? extends Annotation> firstDeclaredScope(Class<?> clazz) {
        for (Annotation annotation : clazz.getDeclaredAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isScopeAnnotationType(annotationType)) {
                return normalizeSingletonToApplicationScoped(annotationType);
            }
        }
        return null;
    }

    public Class<? extends Annotation> extractScopeFromStereotype(Class<? extends Annotation> stereotypeClass) {
        if (knowledgeBase.isRegisteredStereotype(stereotypeClass)) {
            Set<Annotation> definition = knowledgeBase.getStereotypeDefinition(stereotypeClass);
            if (definition != null) {
                for (Annotation annotation : definition) {
                    if (annotation == null) {
                        continue;
                    }
                    Class<? extends Annotation> annotationType = annotation.annotationType();
                    if (validator.isScopeAnnotationType(annotationType)) {
                        return normalizeSingletonToApplicationScoped(annotationType);
                    }
                }
            }
        }

        for (Annotation annotation : stereotypeClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isScopeAnnotationType(annotationType)) {
                return normalizeSingletonToApplicationScoped(annotationType);
            }
        }

        for (Annotation annotation : stereotypeClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isStereotypeAnnotationType(annotationType)) {
                Class<? extends Annotation> nestedScope = extractScopeFromStereotype(annotationType);
                if (nestedScope != null) {
                    return nestedScope;
                }
            }
        }

        return null;
    }

    private Set<Annotation> extractQualifiersFromStereotype(Class<? extends Annotation> stereotypeClass) {
        Set<Annotation> qualifiers = extractQualifierAnnotations(stereotypeClass.getAnnotations())
                .stream()
                .filter(annotation -> !hasNamedAnnotation(annotation.annotationType()))
                .collect(Collectors.toSet());

        if (knowledgeBase.isRegisteredStereotype(stereotypeClass)) {
            Set<Annotation> definition = knowledgeBase.getStereotypeDefinition(stereotypeClass);
            if (definition != null) {
                for (Annotation annotation : definition) {
                    if (annotation == null) {
                        continue;
                    }
                    Class<? extends Annotation> annotationType = annotation.annotationType();
                    if (validator.isQualifierAnnotationType(annotationType) && !hasNamedAnnotation(annotationType)) {
                        qualifiers.add(annotation);
                    }
                }
            }
        }

        for (Annotation annotation : stereotypeClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isStereotypeAnnotationType(annotationType)) {
                qualifiers.addAll(extractQualifiersFromStereotype(annotationType));
            }
        }

        return qualifiers;
    }
}
