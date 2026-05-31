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
@DisplayName("5.2.4 - Raw bean type to parameterized required type assignability (TCK parity)")
class RawBeanTypeParameterizedRequiredTypeTest {

    private final TypeLiteral<Foo<Object>> fooObjectLiteral = new TypeLiteral<Foo<Object>>() {
    };
    private final TypeLiteral<Foo<Integer>> fooIntegerLiteral = new TypeLiteral<Foo<Integer>>() {
    };
    private final TypeLiteral<Bar<Object, Integer>> barObjectIntegerLiteral = new TypeLiteral<Bar<Object, Integer>>() {
    };
    private final TypeLiteral<Bar<Object, Object>> barObjectLiteral = new TypeLiteral<Bar<Object, Object>>() {
    };

    @Test
    @DisplayName("5.2.4(g) - Raw bean type is not assignable when required type params are not Object/unbounded variables")
    void shouldNotAssignRawBeanTypeToParameterizedRequiredTypeForNonAssignableTypeParameters() {
        Syringe syringe = newSyringe();

        assertEquals(0, getBeans(syringe, fooIntegerLiteral).size());
        assertEquals(0, getBeans(syringe, barObjectIntegerLiteral).size());
    }

    @Test
    @DisplayName("5.2.4(g) - Raw bean type is assignable when required type params are Object/unbounded variables")
    void shouldAssignRawBeanTypeToParameterizedRequiredTypeForAssignableTypeParameters() {
        Syringe syringe = newSyringe();

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
