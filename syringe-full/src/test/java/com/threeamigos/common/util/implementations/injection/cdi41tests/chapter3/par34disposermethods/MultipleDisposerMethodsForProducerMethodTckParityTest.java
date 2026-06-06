package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.4.3 - TCK parity: MultipleDisposerMethodsForProducerMethodTest")
class MultipleDisposerMethodsForProducerMethodTckParityTest {

    @Test
    @DisplayName("3.4.3 (ba) - multiple disposer methods for one producer method is a definition error")
    void multipleDisposerMethodsForSingleProducerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMultipleDisposersBean.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    static class Product {
    }

    @Dependent
    static class InvalidMultipleDisposersBean {
        @Produces
        Product produce() {
            return new Product();
        }

        void disposeOne(@Disposes Product product) {
        }

        void disposeTwo(@Disposes Product product) {
        }
    }
}
