package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationLiteral;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnotationLiteral unit tests")
class AnnotationLiteralUnitTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface MarkerAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @interface AnnotationWithMember {
        String value() default "";
    }

    interface NotAnAnnotation extends java.lang.annotation.Annotation {
        String annotationType(String param);
        boolean equals();
        int hashCode(int param);
        String toString(String param);
    }

    @Test
    @DisplayName("annotationType() should return the annotation class")
    void annotationTypeShouldReturnAnnotationClass() {
        MarkerAnnotation annotation = AnnotationLiteral.of(MarkerAnnotation.class);
        assertEquals(MarkerAnnotation.class, annotation.annotationType());
    }

    @Test
    @DisplayName("equals() should return true for instances of the same annotation class")
    void equalsShouldReturnTrueForSameAnnotationClass() {
        MarkerAnnotation annotation = AnnotationLiteral.of(MarkerAnnotation.class);
        MarkerAnnotation other = AnnotationLiteral.of(MarkerAnnotation.class);

        // It should equal itself
        assertEquals(annotation, annotation);
        // It should equal another proxy of the same type
        assertEquals(annotation, other);
    }

    @Test
    @DisplayName("equals() should return false for different types or null")
    void equalsShouldReturnFalseForDifferentTypesOrNull() {
        MarkerAnnotation annotation = AnnotationLiteral.of(MarkerAnnotation.class);

        assertNotEquals("not an annotation", annotation);
        assertNotEquals(null, annotation);
        assertNotEquals(null, annotation);
        assertNotEquals(annotation, AnnotationLiteral.of(AnnotationWithMember.class));
    }

    @Test
    @DisplayName("hashCode() should return 0")
    void hashCodeShouldReturnZero() {
        MarkerAnnotation annotation = AnnotationLiteral.of(MarkerAnnotation.class);
        assertEquals(0, annotation.hashCode());
    }

    @Test
    @DisplayName("toString() should return the expected format")
    void toStringShouldReturnExpectedFormat() {
        MarkerAnnotation annotation = AnnotationLiteral.of(MarkerAnnotation.class);
        assertEquals("@" + MarkerAnnotation.class.getName() + "()", annotation.toString());
    }

    @Test
    @DisplayName("calling a method with members should throw UnsupportedOperationException")
    void callingMethodWithMembersShouldThrowException() {
        AnnotationWithMember annotation = AnnotationLiteral.of(AnnotationWithMember.class);
        assertThrows(UnsupportedOperationException.class, annotation::value);
    }

    @Test
    @DisplayName("calling standard methods with wrong parameter count should throw UnsupportedOperationException")
    void callingStandardMethodsWithWrongParameterCountShouldThrowException() throws Throwable {
        // We cast to java.lang.annotation.Annotation to satisfy AnnotationLiteral.of
        // but the proxy will also implement NotAnAnnotation if we could pass it.
        // However, AnnotationLiteral.of(Class<T>) creates a proxy for EXACTLY that class.
        
        // So we need to use the InvocationHandler directly to test these branches,
        // as we cannot create a proxy that implements NotAnAnnotation via AnnotationLiteral.of(MarkerAnnotation.class).
        
        MarkerAnnotation annotation = AnnotationLiteral.of(MarkerAnnotation.class);
        java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(annotation);

        java.lang.reflect.Method wrongAnnotationType = NotAnAnnotation.class.getMethod("annotationType", String.class);
        assertThrows(UnsupportedOperationException.class, () -> handler.invoke(annotation, wrongAnnotationType, new Object[]{"param"}));

        java.lang.reflect.Method wrongEquals = NotAnAnnotation.class.getMethod("equals");
        assertThrows(UnsupportedOperationException.class, () -> handler.invoke(annotation, wrongEquals, new Object[0]));

        java.lang.reflect.Method wrongHashCode = NotAnAnnotation.class.getMethod("hashCode", int.class);
        assertThrows(UnsupportedOperationException.class, () -> handler.invoke(annotation, wrongHashCode, new Object[]{1}));

        java.lang.reflect.Method wrongToString = NotAnAnnotation.class.getMethod("toString", String.class);
        assertThrows(UnsupportedOperationException.class, () -> handler.invoke(annotation, wrongToString, new Object[]{"param"}));
    }
}