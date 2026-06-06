package com.threeamigos.common.util.implementations.injection.annotations;

import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;

import java.lang.annotation.Annotation;
import java.util.Collection;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasAlternativeAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasStereotypeAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.StereotypesHelper.declaresAlternative;

/**
 * Helper for alternative-related annotation decisions.
 */
public class AlternativesHelper {

    private AlternativesHelper() {}

    /**
     * Checks if the given class is an alternative declaration.
     * @param beanClass the class to check
     * @return true if the class is an alternative declaration, false otherwise
     */
    public static boolean isAlternativeViaAnnotationOrStereotype(Class<?> beanClass) {
        if (beanClass == null) {
            return false;
        }
        if (hasAlternativeAnnotation(beanClass)) {
            return true;
        }
        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasStereotypeAnnotation(annotationType) && declaresAlternative(annotationType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAlternativeEnabledInBeansXml(String className, Collection<BeansXml> beansXmlConfigurations) {
        if (className == null || className.isEmpty() || beansXmlConfigurations == null) {
            return false;
        }

        for (BeansXml beansXml : beansXmlConfigurations) {
            if (beansXml.getAlternatives() != null) {
                if (beansXml.getAlternatives().getClasses().contains(className)) {
                    return true;
                }
                if (beansXml.getAlternatives().getStereotypes().contains(className)) {
                    return true;
                }
            }
        }

        return false;
    }
}
