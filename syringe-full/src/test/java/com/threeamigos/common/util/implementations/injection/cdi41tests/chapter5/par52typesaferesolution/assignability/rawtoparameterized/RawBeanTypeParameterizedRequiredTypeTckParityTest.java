package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.rawtoparameterized;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("serial")
@DisplayName("5.2.4(g) - TCK parity for raw bean type and parameterized required type")
class RawBeanTypeParameterizedRequiredTypeTckParityTest<T, X extends Number> {

    private final TypeLiteral<Foo<T>> fooUnboundedTypeVariableLiteral = new TypeLiteral<Foo<T>>() {
    };
    private final TypeLiteral<Foo<X>> fooBoundedTypeVariableLiteral = new TypeLiteral<Foo<X>>() {
    };
    private final TypeLiteral<Foo<Object>> fooObjectLiteral = new TypeLiteral<Foo<Object>>() {
    };
    private final TypeLiteral<Foo<Integer>> fooIntegerLiteral = new TypeLiteral<Foo<Integer>>() {
    };
    private final TypeLiteral<Bar<String, T>> barStringUnboundedTypeVariableLiteral = new TypeLiteral<Bar<String, T>>() {
    };
    private final TypeLiteral<Bar<String, X>> barStringBoundedTypeVariableLiteral = new TypeLiteral<Bar<String, X>>() {
    };
    private final TypeLiteral<Bar<Object, X>> barObjectBoundedTypeVariableLiteral = new TypeLiteral<Bar<Object, X>>() {
    };
    private final TypeLiteral<Bar<Object, Integer>> barObjectIntegerLiteral = new TypeLiteral<Bar<Object, Integer>>() {
    };
    private final TypeLiteral<Bar<Object, Object>> barObjectLiteral = new TypeLiteral<Bar<Object, Object>>() {
    };

    @Test
    @DisplayName("testNotAssignableTypeParams")
    void testNotAssignableTypeParams() {
        Syringe syringe = newSyringe();
        assertEquals(0, getBeans(syringe, fooIntegerLiteral).size());
        assertEquals(0, getBeans(syringe, fooBoundedTypeVariableLiteral).size());
        assertEquals(0, getBeans(syringe, barStringUnboundedTypeVariableLiteral).size());
        assertEquals(0, getBeans(syringe, barStringBoundedTypeVariableLiteral).size());
        assertEquals(0, getBeans(syringe, barObjectBoundedTypeVariableLiteral).size());
        assertEquals(0, getBeans(syringe, barObjectIntegerLiteral).size());
    }

    @Test
    @DisplayName("testAssignableTypeParams")
    void testAssignableTypeParams() {
        Syringe syringe = newSyringe();
        assertEquals(1, getBeans(syringe, fooUnboundedTypeVariableLiteral).size());
        assertEquals(1, getBeans(syringe, fooObjectLiteral).size());
        assertEquals(1, getBeans(syringe, barObjectLiteral).size());
    }

    private static Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RawProducer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private static Set<Bean<?>> getBeans(Syringe syringe, TypeLiteral<?> requiredType) {
        return syringe.getBeanManager().getBeans(requiredType.getType());
    }
}
