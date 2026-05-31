package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.enterprise.util.TypeLiteral;
import java.lang.reflect.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RawTypeExtractor unit test")
class RawTypeExtractorUnitTest {

    @Test
    @DisplayName("getRawType should return the class when input is a Class")
    void shouldReturnClassWhenInputIsClass() {
        assertEquals(String.class, RawTypeExtractor.getRawType(String.class));
        assertEquals(Integer.class, RawTypeExtractor.getRawType(Integer.class));
    }

    @Test
    @DisplayName("getRawType should return the raw type when input is a ParameterizedType")
    void shouldReturnRawTypeWhenInputIsParameterizedType() {
        Type type = new TypeLiteral<List<String>>() {}.getType();
        assertEquals(List.class, RawTypeExtractor.getRawType(type));
    }

    @Test
    @DisplayName("getRawType should return the array class when input is a GenericArrayType")
    void shouldReturnArrayClassWhenInputIsGenericArrayType() {
        // List<String>[]
        Type componentType = new TypeLiteral<List<String>>() {}.getType();
        GenericArrayType genericArrayType = () -> componentType;

        Class<?> rawType = RawTypeExtractor.getRawType(genericArrayType);
        assertTrue(rawType.isArray());
        assertEquals(List.class, rawType.getComponentType());
    }

    @Test
    @DisplayName("getRawType should return the bound when input is a TypeVariable")
    @SuppressWarnings("unused")
    <T extends Number> void shouldReturnBoundWhenInputIsTypeVariable() throws NoSuchMethodException {
        Method method = RawTypeExtractorUnitTest.class.getDeclaredMethod("shouldReturnBoundWhenInputIsTypeVariable");
        TypeVariable<?> typeVariable = method.getTypeParameters()[0];

        assertEquals(Number.class, RawTypeExtractor.getRawType(typeVariable));
    }

    @Test
    @DisplayName("getRawType should return the upper bound when input is a WildcardType")
    void shouldReturnUpperBoundWhenInputIsWildcardType() {
        // List<? extends Number>
        ParameterizedType listType = (ParameterizedType) new TypeLiteral<List<? extends Number>>() {}.getType();
        WildcardType wildcardType = (WildcardType) listType.getActualTypeArguments()[0];

        assertEquals(Number.class, RawTypeExtractor.getRawType(wildcardType));
    }

    @Test
    @DisplayName("getRawType should throw IllegalArgumentException for unsupported types")
    void shouldThrowExceptionForUnsupportedType() {
        Type unsupportedType = new Type() {
            @Override
            public String getTypeName() {
                return "Unsupported";
            }
        };

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            RawTypeExtractor.getRawType(unsupportedType)
        );

        assertTrue(exception.getMessage().contains("Unsupported type"));
    }
}