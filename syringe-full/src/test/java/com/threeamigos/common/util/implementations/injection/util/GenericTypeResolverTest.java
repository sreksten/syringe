package com.threeamigos.common.util.implementations.injection.util;

import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericTypeResolverTest {

    @Test
    void shouldResolveParameterizedTypeWithStructuralEquality() throws NoSuchFieldException {
        Field field = GenericParent.class.getDeclaredField("t2BazList");
        Type declaredType = field.getGenericType();

        Type resolved = GenericTypeResolver.resolve(declaredType, Foo.class, GenericParent.class);
        Type expected = new TypeLiteral<Baz<List<Qux>>>() {}.getType();

        assertEquals(expected, resolved);
        assertEquals(expected.getTypeName(), resolved.getTypeName());
        assertTrue(resolved.toString().contains("Baz<java.util.List<"));
    }

    @Test
    void shouldResolveObservedTypeWithStructuralEquality() throws NoSuchMethodException {
        Type declaredType = GenericParent.class
                .getDeclaredMethod("observe", Baz.class)
                .getGenericParameterTypes()[0];

        Type resolved = GenericTypeResolver.resolve(declaredType, Foo.class, GenericParent.class);
        Type expected = new TypeLiteral<Baz<String>>() {}.getType();

        assertEquals(expected, resolved);
        assertEquals(expected.getTypeName(), resolved.getTypeName());
    }

    static class Baz<T> {
    }

    static class Qux {
    }

    static class GenericParent<T1, T2> {
        Baz<List<T2>> t2BazList;

        void observe(Baz<T1> observed) {
            // no-op
        }
    }

    static class Foo extends GenericParent<String, Qux> {
    }
}
