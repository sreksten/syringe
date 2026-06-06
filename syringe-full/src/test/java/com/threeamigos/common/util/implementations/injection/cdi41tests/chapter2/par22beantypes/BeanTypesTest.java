package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par22beantypes;

import com.threeamigos.common.util.implementations.injection.discovery.BeanTypesExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("2.2 - Bean Types")
public class BeanTypesTest {

    private BeanTypesExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BeanTypesExtractor();
    }

    @Test
    @DisplayName("2.2 - Bookshop has four bean types")
    void bookshopHasFourTypes() {
        // Given
        BeanTypesExtractor.ExtractionResult result = extractor.extractManagedBeanTypes(Bookshop.class);
        // When
        List<Type> expected = Arrays.asList(Bookshop.class, Business.class,
                new TypeToken<Shop<Book>>() {}.getType(), Object.class);
        // Then
        assertFalse(result.hasDefinitionErrors());
        assertEquals(4, result.getTypes().size());
        assertThat("Collections should contain the same elements", result.getTypes(),
                containsInAnyOrder(expected.toArray()));
    }

    @Test
    @DisplayName("2.2.1 - GenericBookshop: parameterized type with type variable (Shop<T>) is legal")
    void parameterizedTypeWithTypeVariableIsLegal() {
        // Given
        BeanTypesExtractor.ExtractionResult result = extractor.extractManagedBeanTypes(GenericBookshop.class);
        // When
        boolean hasTypeVariableInterface = result.getTypes().stream()
                .filter(t -> t instanceof ParameterizedType)
                .map(t -> (ParameterizedType) t)
                .anyMatch(pt -> pt.getRawType().equals(Shop.class)
                        && pt.getActualTypeArguments()[0] instanceof TypeVariable);
        // Then
        assertFalse(result.hasDefinitionErrors());
        assertTrue(hasTypeVariableInterface, "Shop<T> should be present as a bean type");
    }

    @Test
    @DisplayName("2.2.1 - WildcardBookshopProducer: wildcard bean type is pruned")
    void parameterizedTypeWithWildcardProducerIsIllegal() {
        // Given
        Type producerType = producerReturnType(WildcardBookshopProducer.class, "shop");
        // When
        BeanTypesExtractor.ExtractionResult result = extractor.extractProducerBeanTypes(producerType);
        // Then
        boolean hasWildcardInterface = result.getTypes().stream()
                .filter(t -> t instanceof ParameterizedType)
                .map(t -> (ParameterizedType) t)
                .anyMatch(pt -> pt.getRawType().equals(Shop.class)
                        && pt.getActualTypeArguments()[0] instanceof WildcardType);
        assertFalse(hasWildcardInterface, "Shop<? extends Book> should not be in producer bean types");

        List<Type> expected = Arrays.asList(Shop.class, Object.class);
        assertThat("Collections should contain the same elements", result.getTypes(),
                containsInAnyOrder(expected.toArray()));
    }

    @Test
    @DisplayName("2.2.1 - ConcreteArrayShop: array type with legal component is legal")
    void arrayTypeWithConcreteComponentIsLegal() {
        // Given
        BeanTypesExtractor.ExtractionResult result = extractor.extractManagedBeanTypes(ConcreteArrayShop.class);
        // When
        boolean hasArrayInterface = result.getTypes().stream()
                .filter(t -> t instanceof ParameterizedType)
                .map(t -> (ParameterizedType) t)
                .anyMatch(pt -> pt.getRawType().equals(Shop.class)
                        && Book[].class.equals(pt.getActualTypeArguments()[0]));
        // Then
        assertFalse(result.hasDefinitionErrors());
        assertTrue(hasArrayInterface, "Shop<Book[]> should be present as a legal bean type");
    }

    @Test
    @DisplayName("2.2.1 - VariableArrayShop: Array type with non-legal component (type variable) is not a legal bean type")
    void arrayTypeWithTypeVariableComponentIsIllegal() {
        // Given
        BeanTypesExtractor.ExtractionResult result = extractor.extractManagedBeanTypes(VariableArrayShop.class);
        // When
        boolean hasIllegalArrayInterface = result.getTypes().stream()
                .filter(t -> t instanceof ParameterizedType)
                .map(t -> (ParameterizedType) t)
                .anyMatch(pt -> pt.getRawType().equals(Shop.class) && hasTypeVariableArrayArgument(pt));
        // Then
        assertFalse(result.hasDefinitionErrors());
        assertFalse(hasIllegalArrayInterface, "Shop<T[]> should not be a legal bean type when component is a type variable");

        assertEquals(2, result.getTypes().size());
        List<Type> expected = Arrays.asList(VariableArrayShop.class, Object.class);
        assertThat("Collections should contain the same elements", result.getTypes(),
                containsInAnyOrder(expected.toArray()));
    }

    @Test
    @DisplayName("2.2.2 - TypedBookshop has two bean types")
    void typedBookshopHasTwoTypes() {
        // Given
        BeanTypesExtractor.ExtractionResult result = extractor.extractManagedBeanTypes(TypedBookshop.class);
        // When
        List<Type> expected = Arrays.asList(Business.class, Object.class);
        // Then
        assertFalse(result.hasDefinitionErrors());
        assertEquals(2, result.getTypes().size());
        assertThat("Collections should contain the same elements", result.getTypes(),
                containsInAnyOrder(expected.toArray()));
    }

    @Test
    @DisplayName("2.2.2 - EmptyTypedBookshop has one bean type")
    void emptyTypedBookshopHasOneType() {
        // Given
        BeanTypesExtractor.ExtractionResult result = extractor.extractManagedBeanTypes(EmptyTypedBookshop.class);
        // When
        List<Type> expected = Collections.singletonList(Object.class);
        // Then
        assertFalse(result.hasDefinitionErrors());
        assertEquals(1, result.getTypes().size());
        assertThat("Collections should contain the same elements", result.getTypes(),
                containsInAnyOrder(expected.toArray()));
    }

    abstract static class TypeToken<T> {
        private final Type type;

        protected TypeToken() {
            // Get the superclass' parameterized type (e.g., TypeToken<Shop<Book>>)
            Type superclass = getClass().getGenericSuperclass();
            this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        }

        public Type getType() {
            return type;
        }
    }

    private boolean hasTypeVariableArrayArgument(ParameterizedType pt) {
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) arg;
            return gat.getGenericComponentType() instanceof TypeVariable;
        }
        return false;
    }

    private Type producerReturnType(Class<?> declaringClass, String methodName) {
        try {
            return declaringClass.getDeclaredMethod(methodName).getGenericReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Producer method not found: " + declaringClass.getName() + "#" + methodName, e);
        }
    }

}
