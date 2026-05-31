package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Decorated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("5.5.8 - TCK parity for built-in metadata type parameters in decorators")
@Execution(ExecutionMode.SAME_THREAD)
public class DecoratorBeanMetadataTypeParameterTckParityTest {

    @Test
    @DisplayName("5.5.8 / DecoratorTypeParamFieldTest - Decorator<T> type parameter must match declaring decorator bean type")
    void shouldRejectDecoratorMetadataFieldWithMismatchedTypeParameter() {
        Syringe syringe = newSyringe(Milk.class, MilkBean.class, InvalidDecoratorMetadataField.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 / DecoratorTypeParamConstructorTest - Decorator<T> constructor parameter must match declaring decorator bean type")
    void shouldRejectDecoratorMetadataConstructorWithMismatchedTypeParameter() {
        Syringe syringe = newSyringe(Milk.class, MilkBean.class, InvalidDecoratorMetadataConstructor.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 / DecoratoredBeanTypeParamFieldTest - @Decorated Bean<T> type parameter must match declaring decorated bean type")
    void shouldRejectDecoratedBeanMetadataFieldWithMismatchedTypeParameter() {
        Syringe syringe = newSyringe(Milk.class, MilkBean.class, InvalidDecoratedBeanMetadataField.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    public interface Milk {
        void ping();
    }

    public static class Cream {
    }

    @Dependent
    public static class MilkBean implements Milk {
        @Override
        public void ping() {
        }
    }

    @Decorator
    @Priority(100)
    public static class InvalidDecoratorMetadataField implements Milk {
        @Inject
        @Delegate
        Milk delegate;

        @Inject
        jakarta.enterprise.inject.spi.Decorator<Cream> decorator;

        @Override
        public void ping() {
            delegate.ping();
        }
    }

    @Decorator
    @Priority(100)
    public static class InvalidDecoratorMetadataConstructor implements Milk {
        @Inject
        @Delegate
        Milk delegate;

        @Inject
        public InvalidDecoratorMetadataConstructor(jakarta.enterprise.inject.spi.Decorator<Cream> decorator) {
        }

        @Override
        public void ping() {
            delegate.ping();
        }
    }

    @Decorator
    @Priority(100)
    public static class InvalidDecoratedBeanMetadataField implements Milk {
        @Inject
        @Delegate
        Milk delegate;

        @Inject
        @Decorated
        Bean<Cream> bean;

        @Override
        public void ping() {
            delegate.ping();
        }
    }
}
