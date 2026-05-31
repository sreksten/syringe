package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.5 - TCK parity: ConstructorHasDisposesParameterTest")
class ConstructorHasDisposesParameterTckParityTest {

    @Test
    @DisplayName("3.5 (da) - constructor parameter annotated @Disposes is a definition error")
    void constructorParameterAnnotatedDisposesIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DisposingConstructorBean.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    static class Duck {
    }

    @Dependent
    static class DisposingConstructorBean {
        DisposingConstructorBean(@Disposes Duck duck) {
        }
    }
}
