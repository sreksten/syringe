package com.threeamigos.common.util.implementations.injection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static com.threeamigos.common.util.implementations.injection.resolution.TypeChecker.TypePair;

@DisplayName("TypePair unit tests")
class TypePairUnitTest {

    @Test
    @DisplayName("Constructor should throw NullPointerException if target is null")
    void constructorShouldThrowExceptionIfTargetIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, 
            () -> new TypePair(null, List.class));
        assertEquals("target cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Constructor should throw NullPointerException if implementation is null")
    void constructorShouldThrowExceptionIfImplementationIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, 
            () -> new TypePair(String.class, null));
        assertEquals("implementation cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("equals() should return true for the same instance")
    void equalsShouldReturnTrueForSameInstance() {
        TypePair pair = new TypePair(String.class, List.class);
        assertEquals(pair, pair);
    }

    @Test
    @DisplayName("equals() should return false for null or different class")
    void equalsShouldReturnFalseForNullOrDifferentClass() {
        TypePair pair = new TypePair(String.class, List.class);
        assertNotEquals(null, pair);
        assertNotEquals("not a type pair", pair);
    }

    @Test
    @DisplayName("equals() should return true for pairs with same target and implementation")
    void equalsShouldReturnTrueForSameContent() {
        TypePair pair1 = new TypePair(String.class, List.class);
        TypePair pair2 = new TypePair(String.class, List.class);

        assertEquals(pair1, pair2);
        assertEquals(pair1.hashCode(), pair2.hashCode());
    }

    @Test
    @DisplayName("equals() should return false for pairs with different targets")
    void equalsShouldReturnFalseForDifferentTargets() {
        TypePair pair1 = new TypePair(String.class, List.class);
        TypePair pair2 = new TypePair(Integer.class, List.class);

        assertNotEquals(pair1, pair2);
    }

    @Test
    @DisplayName("equals() should return false for pairs with different implementations")
    void equalsShouldReturnFalseForDifferentImplementations() {
        TypePair pair1 = new TypePair(String.class, List.class);
        TypePair pair2 = new TypePair(String.class, Iterable.class);

        assertNotEquals(pair1, pair2);
    }

    @Test
    @DisplayName("hashCode() should be consistent")
    void hashCodeShouldBeConsistent() {
        TypePair pair = new TypePair(String.class, List.class);
        int initialHashCode = pair.hashCode();
        assertEquals(initialHashCode, pair.hashCode());
    }
}