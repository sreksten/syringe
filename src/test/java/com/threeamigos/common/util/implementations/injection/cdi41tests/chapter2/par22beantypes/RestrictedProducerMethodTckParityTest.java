package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par22beantypes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("2.2.2 - TCK parity: RestrictedProducerMethodTest")
class RestrictedProducerMethodTckParityTest {

    @Test
    @DisplayName("2.2.2 (m) - producer @Typed value must be unrestricted bean type")
    void producerMethodTypedValueMustBeUnrestrictedBeanType() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), BoulderMethodProducer.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Dependent
    static class Animal {
    }

    static class Boulder {
        Boulder(String name) {
        }
    }

    @Dependent
    static class BoulderMethodProducer {
        @Produces
        @Typed(Animal.class)
        Boulder produce() {
            return new Boulder(null);
        }
    }
}
