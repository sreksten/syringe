package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;

/**
 * Extractor helpers for annotation instances and values.
 */
public final class AnnotationExtractors {

    private AnnotationExtractors() {
    }

    public static <T extends Annotation> T getTypedAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.TYPED);
    }

    public static <T extends Annotation> T getPriorityAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.PRIORITY);
    }

    public static <T extends Annotation> T getNamedAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.NAMED);
    }

    public static Target getTargetAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(Target.class);
    }

    public static java.lang.annotation.Retention getRetentionAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(java.lang.annotation.Retention.class);
    }

    public static java.lang.annotation.Repeatable getRepeatableAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(java.lang.annotation.Repeatable.class);
    }

    public static <T extends Annotation> T getObservesAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.OBSERVES);
    }

    public static <T extends Annotation> T getObservesAsyncAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.OBSERVES_ASYNC);
    }

    public static jakarta.enterprise.inject.build.compatible.spi.Registration getRegistrationAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(jakarta.enterprise.inject.build.compatible.spi.Registration.class);
    }

    public static jakarta.enterprise.inject.build.compatible.spi.Enhancement getEnhancementAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(jakarta.enterprise.inject.build.compatible.spi.Enhancement.class);
    }

    public static jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent
    getSkipIfPortableExtensionPresentAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent.class);
    }

    public static Integer getPriorityValue(AnnotatedElement element) {
        Annotation priority = getPriorityAnnotation(element);
        if (priority == null) {
            return null;
        }
        try {
            Object value = priority.annotationType().getMethod("value").invoke(priority);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Boolean getNormalScopePassivatingValue(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        Annotation normalScope = getFirstAnnotation(element, AnnotationsEnum.NORMAL_SCOPE);
        if (normalScope == null) {
            return null;
        }
        try {
            Object value = normalScope.annotationType().getMethod("passivating").invoke(normalScope);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> T getFirstAnnotation(AnnotatedElement element, AnnotationsEnum alias) {
        if (element == null || alias == null) {
            return null;
        }
        for (Class<? extends Annotation> annotationClass : alias.getAnnotations()) {
            T annotation = (T) element.getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }
}
