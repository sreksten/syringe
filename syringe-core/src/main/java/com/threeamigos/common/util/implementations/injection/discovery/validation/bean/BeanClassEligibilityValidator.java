package com.threeamigos.common.util.implementations.injection.discovery.validation.bean;

import com.threeamigos.common.util.implementations.injection.discovery.validation.CDI41BeanValidator;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.inject.spi.Extension;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasVetoedAnnotation;

/**
 * Extracted bean-class eligibility rules for CDI41BeanValidator.
 */
public class BeanClassEligibilityValidator {
    private final CDI41BeanValidator validator;

    public BeanClassEligibilityValidator(CDI41BeanValidator validator) {
        this.validator = validator;
    }

    public boolean isCandidateBeanClass(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        if (clazz == null || hasVetoedAnnotation(clazz) || beanArchiveMode == BeanArchiveMode.NONE ||
                Extension.class.isAssignableFrom(clazz) ||
                clazz.isAnnotation() || clazz.isInterface() || clazz.isEnum() || clazz.isPrimitive() || clazz.isArray()) {
            return false;
        }

        boolean beanDefining = validator.hasBeanDefiningAnnotation(clazz);
        if (validator.isCurrentValidatedTypeOverridden(clazz)) {
            beanDefining = beanDefining || validator.hasBeanDefiningAnnotationFromReflection(clazz);
        }
        if (beanDefining
                || validator.hasDecoratorAnnotation(clazz)
                || validator.hasAlternativeAnnotation(clazz)) {
            if (!validator.hasNoArgsConstructor(clazz) && validator.hasNotInjectConstructor(clazz)) {
                if (validator.hasAnyDisposer(clazz) && !validator.hasAnyProducer(clazz)) {
                    return false;
                }
                return !validator.hasOnlyStaticProducersAndDisposers(clazz);
            }
            return true;
        }

        if (beanArchiveMode == BeanArchiveMode.EXPLICIT) {
            return validator.hasNoArgsConstructor(clazz) || validator.hasResolvableInjectConstructor(clazz);
        }

        return false;
    }
}
