package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasAlternativeAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasStereotypeAnnotation;

/**
 * Helper for stereotype-related annotation decisions.
 */
public class StereotypesHelper {

    private StereotypesHelper() {}

    public static boolean declaresAlternative(Class<? extends Annotation> stereotypeType) {
        return declaresAlternative(stereotypeType, new HashSet<>());
    }

    public static boolean declaresAlternative(Class<? extends Annotation> stereotypeType,
                                        Set<Class<? extends Annotation>> visited) {
        if (stereotypeType == null) {
            return false;
        }

        if (!visited.add(stereotypeType)) {
            return false;
        }

        if (hasAlternativeAnnotation(stereotypeType)) {
            return true;
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasStereotypeAnnotation(metaType) && declaresAlternative(metaType, visited)) {
                return true;
            }
        }

        return false;
    }
}
