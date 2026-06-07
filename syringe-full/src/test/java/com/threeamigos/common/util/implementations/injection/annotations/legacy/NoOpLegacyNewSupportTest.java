package com.threeamigos.common.util.implementations.injection.annotations.legacy;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class NoOpLegacyNewSupportTest {

    @Test
    void enableThrowsCanonicalApiCallMessage() {
        NoOpLegacyNewSupport noOp = new NoOpLegacyNewSupport();

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::enable
        );

        assertTrue(ex.getMessage().startsWith(
                "API call found at Syringe.enableLegacyCdi10New(boolean) but legacy @New support is not available."));
    }

    @Test
    void resolveSelectionThrowsCanonicalUsageMessageWhenNewQualifierIsPresent() {
        NoOpLegacyNewSupport noOp = new NoOpLegacyNewSupport();
        Annotation qualifier = findFieldAnnotation(SampleQualifiers.class, "legacyNew", "javax.enterprise.inject.New");
        assertNotNull(qualifier);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                () -> noOp.resolveSelection(String.class, new Annotation[]{qualifier})
        );

        assertTrue(ex.getMessage().startsWith(
                "@New found at required type java.lang.String but legacy @New support is not available."));
    }

    private Annotation findFieldAnnotation(Class<?> holder, String fieldName, String annotationTypeName) {
        try {
            Field field = holder.getDeclaredField(fieldName);
            for (Annotation annotation : field.getAnnotations()) {
                if (annotation.annotationType().getName().equals(annotationTypeName)) {
                    return annotation;
                }
            }
            throw new IllegalStateException("No annotation " + annotationTypeName + " found on " + fieldName);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing field " + fieldName, e);
        }
    }

    private static class SampleQualifiers {
        @SuppressWarnings("unused")
        @javax.enterprise.inject.New(String.class)
        private String legacyNew;
    }
}
