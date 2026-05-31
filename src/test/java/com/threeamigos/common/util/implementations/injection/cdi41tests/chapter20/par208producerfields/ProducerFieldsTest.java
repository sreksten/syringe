package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par208producerfields;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.8 - Producer fields")
@Execution(ExecutionMode.SAME_THREAD)
public class ProducerFieldsTest {

    @Test
    @DisplayName("20.8 - Decorator may not declare producer fields")
    void shouldRejectProducerFieldDeclaredByDecorator() {
        Syringe syringe = newSyringe(ServiceBean.class, DecoratorWithProducerField.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Collection<Class<?>> allFixtureTypes() {
        return Arrays.<Class<?>>asList(
                Service.class,
                ServiceBean.class,
                DecoratorWithProducerField.class
        );
    }

    public interface Service {
        String ping();
    }

    @Dependent
    public static class ServiceBean implements Service {
        @Override
        public String ping() {
            return "ok";
        }
    }

    @Decorator
    @Dependent
    public static class DecoratorWithProducerField implements Service {
        @Inject
        @Delegate
        Service delegate;

        @Produces
        @Dependent
        ProducedByDecorator invalidProducedField = new ProducedByDecorator();

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    public static class ProducedByDecorator {
    }
}
