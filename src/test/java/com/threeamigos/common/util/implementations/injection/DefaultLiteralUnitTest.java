package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationLiteral;
import com.threeamigos.common.util.implementations.injection.annotations.DefaultLiteral;
import jakarta.enterprise.inject.Default;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultLiteral unit tests")
class DefaultLiteralUnitTest {

    @Test
    @DisplayName("annotationType() should return Default.class")
    void annotationTypeShouldReturnDefaultClass() {
        DefaultLiteral literal = new DefaultLiteral();
        assertEquals(Default.class, literal.annotationType());
    }

    @Test
    @DisplayName("equals() should return true for instances of Default")
    void equalsShouldReturnTrueForDefaultInstances() {
        DefaultLiteral literal = new DefaultLiteral();
        DefaultLiteral otherLiteral = new DefaultLiteral();
        
        // It should equal itself
        assertEquals(literal, literal);
        // It should equal another DefaultLiteral
        assertEquals(literal, otherLiteral);
        
        // It should equal a proxy of @Default if one existed, but DefaultLiteral itself is enough for coverage
    }

    @Test
    @DisplayName("equals() should return false for null or non-Default instances")
    void equalsShouldReturnFalseForNullOrDifferentTypes() {
        DefaultLiteral literal = new DefaultLiteral();

        assertNotEquals(null, literal);
        assertNotEquals("not a default", literal);
        
        // Testing against another annotation
        Annotation otherAnnotation = AnnotationLiteral.of(Override.class);
        assertNotEquals(otherAnnotation, literal);
    }

    @Test
    @DisplayName("hashCode() should return 0")
    void hashCodeShouldReturnZero() {
        DefaultLiteral literal = new DefaultLiteral();
        assertEquals(0, literal.hashCode());
    }
}
